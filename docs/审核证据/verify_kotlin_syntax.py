"""用 Kotlin PSI 检查当前工作区修改文件的语法；不解析类型，不运行 Gradle。"""
import json
import subprocess
import tempfile
from pathlib import Path
from verify_continuation import ROOT, CACHE, jar


def verify_syntax():
    changed = subprocess.check_output(
        ['git', 'diff', '--name-only', 'HEAD', '--', '*.kt'], cwd=ROOT, text=True).splitlines()
    added = subprocess.check_output(
        ['git', 'ls-files', '--others', '--exclude-standard', '--', '*.kt'],
        cwd=ROOT, text=True).splitlines()
    files = sorted({str(ROOT / name) for name in changed + added if (ROOT / name).exists()})
    dependencies = [jar('org.jetbrains.kotlin', 'kotlin-compiler-embeddable', '2.4.10'),
                    jar('org.jetbrains.kotlin', 'kotlin-stdlib', '2.4.10'),
                    jar('org.jetbrains.kotlinx', 'kotlinx-coroutines-core-jvm', '1.8.0')]
    for group, artifact in [('org.jetbrains.intellij.deps', 'trove4j'),
                            ('org.jetbrains', 'annotations')]:
        matches = sorted((CACHE / group / artifact).glob('*/*/*.jar'))
        if matches:
            dependencies.append(str(matches[-1]))
    source = r'''
import java.nio.file.Files;
import java.nio.file.Path;
import org.jetbrains.kotlin.cli.jvm.compiler.EnvironmentConfigFiles;
import org.jetbrains.kotlin.cli.jvm.compiler.KotlinCoreEnvironment;
import org.jetbrains.kotlin.config.CompilerConfiguration;
import org.jetbrains.kotlin.com.intellij.openapi.util.Disposer;
import org.jetbrains.kotlin.com.intellij.psi.PsiErrorElement;
import org.jetbrains.kotlin.com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.kotlin.psi.KtPsiFactory;

public class SyntaxProbe {
    public static void main(String[] args) throws Exception {
        var disposable = Disposer.newDisposable();
        try {
            var environment = KotlinCoreEnvironment.createForProduction(
                disposable, new CompilerConfiguration(), EnvironmentConfigFiles.JVM_CONFIG_FILES);
            var factory = new KtPsiFactory(environment.getProject(), false);
            int errors = 0;
            for (String filename : args) {
                String text = Files.readString(Path.of(filename));
                var file = factory.createFile(Path.of(filename).getFileName().toString(), text);
                for (var error : PsiTreeUtil.findChildrenOfType(file, PsiErrorElement.class)) {
                    long line = text.substring(0, error.getTextOffset()).chars().filter(c -> c == '\n').count() + 1;
                    System.err.println(filename + ":" + line + ": " + error.getErrorDescription());
                    errors++;
                }
            }
            if (errors != 0) throw new IllegalStateException("语法错误数：" + errors);
        } finally {
            Disposer.dispose(disposable);
        }
    }
}
'''
    with tempfile.TemporaryDirectory(prefix='jd-syntax-') as directory:
        folder = Path(directory)
        java = folder / 'SyntaxProbe.java'
        java.write_text(source)
        classpath = ':'.join(dependencies)
        subprocess.run(['javac', '-cp', classpath, '-d', directory, str(java)], check=True)
        subprocess.run(['java', '-cp', directory + ':' + classpath, 'SyntaxProbe'] + files,
                       check=True, timeout=60)
    return {'files': len(files), 'syntax_errors': 0, 'type_check': False}


if __name__ == '__main__':
    print(json.dumps(verify_syntax(), ensure_ascii=False))
