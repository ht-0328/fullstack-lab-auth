// 接続先は環境変数で指定する（compose.test.yaml では認証 API と Keycloak を同じネットワークに立てる）
function fn() {
  karate.configure('connectTimeout', 5000);
  karate.configure('readTimeout', 10000);
  var env = java.lang.System.getenv;
  return {
    baseUrl: env('BASE_URL') || 'http://auth:8080',
    // 登録したユーザーで実際にトークンが取れるかを、認証 API を通さず IdP で確かめるために使う
    keycloakUrl: env('KEYCLOAK_URL') || 'http://keycloak:8080',
    clientId: env('AUTH_API_CLIENT_ID') || 'auth-api',
    clientSecret: env('AUTH_API_CLIENT_SECRET') || ''
  };
}
