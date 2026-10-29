# fullstack-lab-auth

認証 API（Kotlin + Spring Boot）と、一般利用者向け IdP である Keycloak の realm `customer` の定義。[fullstack-lab](https://github.com/ht-0328/fullstack-lab) の一部で、BFF からアカウントの登録・参照・パスワード変更を依頼される内部サービスです。ログイン（トークンの発行）は担当せず、前段（ローカル: oauth2-proxy、AWS: ALB）が Keycloak のログイン画面へ誘導します。設計は fullstack-lab の `docs/design/0005-auth-api-and-signup.md` にあります。

```
auth/
├── openapi/auth.yaml        # 契約（正）
├── keycloak/import/         # realm customer の定義（テストユーザー、OIDC クライアント、認証 API 用のサービスアカウント）
├── client/                  # publish するクライアントライブラリ（auth-client）
├── karate/                  # Karate（本物の Keycloak に対して契約どおりの振る舞いを確認）
├── compose.test.yaml        # 認証 API と Keycloak だけを立てて Karate を実行する
└── src/                     # Spring Boot
```

## 契約が正

[openapi/auth.yaml](openapi/auth.yaml) が契約です。次を生成し、生成コードは git で管理します（手で直さず、仕様を直して生成し直します）。

| 生成物 | 使う側 |
|---|---|
| コントローラーのインターフェースと DTO（`src/generated`） | [CustomerController](src/main/kotlin/com/example/fullstacklab/auth/customer/CustomerController.kt) が `CustomersApi` を実装する |
| クライアント `com.example.fullstacklab:auth-client`（[client/](client/)、生成物は `client/src/generated`） | BFF。Maven リポジトリ経由でバージョン指定で依存する |

仕様を変えたら `./gradlew generate` で生成し直し、生成物も同じコミットに含めます（ホストに JDK が無ければ fullstack-lab の `./scripts/generate.sh`）。`docker build --target test .` はテストの前に生成し直して差分がないことを確かめるので（`check-generated` ステージ）、生成し忘れや手で直した生成コードは CI で落ちます。

契約を変えるときは `openapi/auth.yaml` の `info.version` と [gradle.properties](gradle.properties) の `version` を同時に上げます。

## Keycloak との関係

| 操作 | Keycloak の呼び出し |
|---|---|
| 登録 `POST /auth/v1/customers` | Admin API でユーザーを作り（ユーザー名 = メールアドレス、確認済み）、realm ロール `customer` を付与する。付与に失敗したらユーザーを削除する |
| 参照 `GET /auth/v1/customers/{id}` | Admin API でユーザーを取得する |
| パスワード変更 `PUT /auth/v1/customers/{id}/password` | 現在のパスワードで Resource Owner Password Credentials のトークンが取れるかを確かめてから、Admin API でパスワードを設定する |

Admin API はクライアント `auth-api` のサービスアカウント（`realm-management` の `manage-users` / `view-users` と、realm ロールの ID を引くための `view-realm`）で呼びます。設定は環境変数 `KEYCLOAK_BASE_URL`、`KEYCLOAK_REALM`、`AUTH_API_CLIENT_ID`、`AUTH_API_CLIENT_SECRET` です（[application.yml](src/main/resources/application.yml)）。

realm の定義 [keycloak/import/customer-realm.json](keycloak/import/customer-realm.json) は Keycloak の `--import-realm` で取り込みます。`${...}` は Keycloak が起動時の環境変数で置き換えます。

## テスト

| 種類 | 実行 | 確認すること |
|---|---|---|
| 単体テスト | `docker build --target test .` | [CustomerAccountServiceTest](src/test/kotlin/com/example/fullstacklab/auth/customer/CustomerAccountServiceTest.kt)：トークンの取得と使い回し、ユーザー作成とロール付与の手順と本文、409 / 404 / 現在のパスワード不一致の変換、付与失敗時の削除 |
| API テスト（Karate） | `docker compose -f compose.test.yaml build && docker compose -f compose.test.yaml run --rm karate` | [karate/features/customers.feature](karate/features/customers.feature)：本物の Keycloak に対して、登録 → 参照 → 重複 409 → パスワード変更 → 新しいパスワードでトークンが取れロール `customer` が入っていること、入力エラー 400、未知の ID の 404 |

終わったら `docker compose -f compose.test.yaml down -v` で片付けます。

## コマンド

```bash
docker build --target test .      # 生成 + 単体テスト
./gradlew :client:publish         # クライアントを publish（環境変数は api リポジトリと同じ）
```
