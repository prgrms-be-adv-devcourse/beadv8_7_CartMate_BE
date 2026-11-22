-- Alloy 내장 mysql exporter용 읽기 계정 (이 시험 컨테이너 안에서만 씀)
CREATE USER IF NOT EXISTS 'exporter'@'%' IDENTIFIED BY 'exporter' WITH MAX_USER_CONNECTIONS 2;
GRANT PROCESS, REPLICATION CLIENT ON *.* TO 'exporter'@'%';
