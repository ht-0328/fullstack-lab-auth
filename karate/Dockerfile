# syntax=docker/dockerfile:1
# Karate（API テスト）の実行環境。ホストに JDK は不要。標準の実行ファイル（fat jar）を検証付きで取得する
FROM eclipse-temurin:21-jre-noble
ADD --checksum=sha256:c0126f3ad907e4e92a9944a0bfa4deae96beae599daf8513f47169516b5714c0 \
    https://github.com/karatelabs/karate/releases/download/v2.1.3/karate-2.1.3.jar /opt/karate/karate.jar
WORKDIR /karate
# -w: karate-config.js を探す作業ディレクトリ（読み取り専用でよい）、-o: レポートの出力先
ENTRYPOINT ["java", "-jar", "/opt/karate/karate.jar", "-w", "/karate", "-o", "/tmp/karate-report"]
CMD ["/karate/features"]
