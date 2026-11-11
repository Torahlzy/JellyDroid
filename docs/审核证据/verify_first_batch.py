"""首轮审核的离线证据：只使用内存数据库与自动清理的临时目录。"""

import json
from pathlib import Path
import re
import sqlite3
import subprocess
import tempfile


ROOT = Path(__file__).resolve().parents[2]
DAO = ROOT / "data/src/main/java/dev/jdtech/jellyfin/database/ServerDatabaseDao.kt"
SCHEMAS = ROOT / "data/schemas/dev.jdtech.jellyfin.database.ServerDatabase"
WORKER = ROOT / "core/src/main/java/dev/jdtech/jellyfin/work/DownloadWorker.kt"


def query_for(method):
    source = DAO.read_text()
    pattern = r'@Query\(\s*"([^"]+)"\s*\)\s*suspend fun ' + method + r'\('
    match = re.search(pattern, source)
    if not match:
        raise AssertionError(f"找不到 DAO 查询：{method}")
    return match.group(1)


def create_database(version):
    connection = sqlite3.connect(":memory:")
    connection.execute("PRAGMA foreign_keys = ON")
    schema = json.loads((SCHEMAS / f"{version}.json").read_text())["database"]
    for entity in schema["entities"]:
        table = entity["tableName"]
        connection.execute(entity["createSql"].replace("${TABLE_NAME}", table))
        for index in entity.get("indices", []):
            connection.execute(index["createSql"].replace("${TABLE_NAME}", table))
    return connection


def verify_sync_acknowledgement():
    connection = create_database(9)
    connection.execute(
        "INSERT INTO userdata VALUES (?, ?, ?, ?, ?, ?)",
        ("user-a", "movie-a", False, False, 100, True),
    )
    identity = {"userId": "user-a", "itemId": "movie-a"}
    snapshot = connection.execute(query_for("getUserDataToBeSynced"), identity).fetchone()

    # 服务端尚未响应时，用户产生新的离线进度；直接执行仓库现有 DAO SQL。
    connection.execute(
        query_for("setPlaybackPositionTicks"),
        dict(identity, playbackPositionTicks=200),
    )
    connection.execute(query_for("setUserDataToBeSynced"), dict(identity, toBeSynced=True))

    # 旧快照上传成功后，现有 Worker 无条件清掉标记。
    connection.execute(query_for("setUserDataToBeSynced"), dict(identity, toBeSynced=False))
    pending = connection.execute(query_for("getUserDataToBeSynced"), identity).fetchone()
    local_position = connection.execute("SELECT playbackPositionTicks FROM userdata").fetchone()[0]
    assert snapshot[4] == 100 and local_position == 200 and pending is None

    # 对照：条件确认旧快照时，新的修改仍应保留待同步状态。
    connection.execute(query_for("setUserDataToBeSynced"), dict(identity, toBeSynced=True))
    cursor = connection.execute(
        "UPDATE userdata SET toBeSynced = 0 WHERE userId = ? AND itemId = ? "
        "AND playbackPositionTicks = ? AND played = ? AND favorite = ?",
        ("user-a", "movie-a", snapshot[4], snapshot[2], snapshot[3]),
    )
    assert cursor.rowcount == 0
    assert connection.execute(query_for("getUserDataToBeSynced"), identity).fetchone()
    connection.close()
    return {"uploaded_position": 100, "local_position": 200, "pending_after_current_ack": False}


def verify_migration_8_9():
    connection = create_database(8)
    expected = create_database(9)
    migration_file = ROOT / "data/src/main/java/dev/jdtech/jellyfin/database/ServerDatabase.kt"
    source = migration_file.read_text().split("val MIGRATION_8_9 =", 1)[1]
    statements = re.findall(r'connection\.execSQL\(\s*"([^"]+)"\s*\)', source)
    assert len(statements) == 8

    # 旧 schema 的两张重建表各放一行，核对路径保留及旧任务关联清除。
    for table in ("sources", "mediastreams"):
        columns = connection.execute(f"PRAGMA table_info({table})").fetchall()
        values = []
        for _, name, kind, required, default, primary in columns:
            if name == "id":
                values.append(f"{table}-a")
            elif name == "path":
                values.append(f"/isolated/{table}-a.mkv")
            elif name == "downloadId":
                values.append(123)
            elif not required:
                values.append(None)
            elif kind == "INTEGER":
                values.append(0)
            else:
                values.append("fixture")
        connection.execute(f"INSERT INTO {table} VALUES ({','.join('?' for _ in values)})", values)

    for statement in statements:
        connection.execute(statement)
    for table in ("sources", "mediastreams"):
        actual_columns = connection.execute(f"PRAGMA table_info({table})").fetchall()
        expected_columns = expected.execute(f"PRAGMA table_info({table})").fetchall()
        assert actual_columns == expected_columns, table
        path, task_id = connection.execute(f"SELECT path, downloadTaskId FROM {table}").fetchone()
        assert path == f"/isolated/{table}-a.mkv" and task_id is None
    connection.close()
    expected.close()
    return {"tables_checked": ["sources", "mediastreams"], "result": "路径保留，结构匹配 schema 9"}


def verify_file_collision():
    source = WORKER.read_text()
    assert "while (candidate.exists() && index <= MAX_DUPLICATE_INDEX)" in source
    assert "temporaryFile.renameTo(finalFile)" in source
    assert re.search(r"MAX_DUPLICATE_INDEX = 99\b", source)

    # 使用与 Kotlin File 相同的 java.io.File，在 JVM 上核对命名与 renameTo 的组合行为。
    java_source = r'''
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;

class DownloadFileCollision {
    static File resolveFinalFile(File temporaryFile, String desiredName) {
        File directory = temporaryFile.getParentFile();
        File candidate = new File(directory, desiredName);
        int index = 2;
        while (candidate.exists() && index <= 99) {
            int dot = desiredName.lastIndexOf('.');
            String name = desiredName.substring(0, dot) + " (" + index + ")" + desiredName.substring(dot);
            candidate = new File(directory, name);
            index++;
        }
        return candidate;
    }

    public static void main(String[] args) throws Exception {
        Path directory = Path.of(args[0]);
        File first = directory.resolve("first.download").toFile();
        File second = directory.resolve("second.download").toFile();
        Files.writeString(first.toPath(), "first-media");
        Files.writeString(second.toPath(), "second-media");
        File firstTarget = resolveFinalFile(first, "shared.mkv");
        File secondTarget = resolveFinalFile(second, "shared.mkv");
        if (!firstTarget.equals(secondTarget)) throw new AssertionError("没有构造出并发窗口");
        if (!first.renameTo(firstTarget) || !second.renameTo(secondTarget)) throw new AssertionError("重命名失败");
        if (!Files.readString(firstTarget.toPath()).equals("second-media")) throw new AssertionError("未复现覆盖");

        Files.writeString(directory.resolve("full.mkv"), "original");
        for (int i = 2; i <= 99; i++) Files.writeString(directory.resolve("full (" + i + ").mkv"), "original");
        File last = directory.resolve("last.download").toFile();
        Files.writeString(last.toPath(), "replacement");
        File occupiedTarget = resolveFinalFile(last, "full.mkv");
        if (!occupiedTarget.exists() || !last.renameTo(occupiedTarget)) throw new AssertionError("未复现上限覆盖");
        Files.writeString(directory.resolve("result.txt"), "同名并发窗口与编号耗尽均覆盖已有文件");
    }
}
'''
    with tempfile.TemporaryDirectory(prefix="jellydroid-audit-") as temporary:
        directory = Path(temporary)
        java_file = directory / "DownloadFileCollision.java"
        java_file.write_text(java_source)
        subprocess.run(["java", str(java_file), str(directory)], check=True, capture_output=True, text=True)
        result = (directory / "result.txt").read_text()
    return {"result": result, "limit": "等价 JVM 文件探针；未运行 Android Worker"}


if __name__ == "__main__":
    results = {
        "sync_acknowledgement": verify_sync_acknowledgement(),
        "migration_8_9": verify_migration_8_9(),
        "download_file_collision": verify_file_collision(),
    }
    print(json.dumps(results, ensure_ascii=False, indent=2))
