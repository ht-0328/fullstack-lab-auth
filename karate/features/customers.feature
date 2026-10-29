Feature: 認証 API（契約 openapi/auth.yaml に沿った振る舞い。Keycloak は本物を使う）

  Background:
    * url baseUrl
    * def email = 'karate-' + java.lang.System.currentTimeMillis() + '@example.com'
    * def newCustomer = { email: '#(email)', familyName: '山田', givenName: '太郎', password: 'password-1' }

  Scenario: 登録 → 参照 → 重複は 409 → パスワード変更 → 新しいパスワードで IdP からトークンが取れる
    Given path 'auth/v1/customers'
    And request newCustomer
    When method post
    Then status 201
    And match response == { id: '#uuid', email: '#(email)', familyName: '山田', givenName: '太郎' }
    * def id = response.id

    Given path 'auth/v1/customers', id
    When method get
    Then status 200
    And match response == { id: '#(id)', email: '#(email)', familyName: '山田', givenName: '太郎' }

    Given path 'auth/v1/customers'
    And request newCustomer
    When method post
    Then status 409
    And match response.status == 409

    Given path 'auth/v1/customers', id, 'password'
    And request { currentPassword: 'not-the-password', newPassword: 'password-2' }
    When method put
    Then status 403

    Given path 'auth/v1/customers', id, 'password'
    And request { currentPassword: 'password-1', newPassword: 'password-2' }
    When method put
    Then status 204

    # 変更後のパスワードで Keycloak がトークンを発行し、付与したロール customer がトークンに入っている
    Given url keycloakUrl + '/realms/customer/protocol/openid-connect/token'
    And form field grant_type = 'password'
    And form field client_id = clientId
    And form field client_secret = clientSecret
    And form field username = email
    And form field password = 'password-2'
    When method post
    Then status 200
    * def payload = response.access_token.split('.')[1]
    * def claims = karate.fromString(new java.lang.String(java.util.Base64.getUrlDecoder().decode(payload)))
    And match claims.sub == id
    And match claims.realm_access.roles contains 'customer'

    # 古いパスワードではもう取れない（Keycloak 26 は資格情報の誤りを 400 invalid_grant で返す）
    Given url keycloakUrl + '/realms/customer/protocol/openid-connect/token'
    And form field grant_type = 'password'
    And form field client_id = clientId
    And form field client_secret = clientSecret
    And form field username = email
    And form field password = 'password-1'
    When method post
    Then status 400
    And match response.error == 'invalid_grant'

  Scenario Outline: 契約に合わない登録は 400（<case>）
    Given path 'auth/v1/customers'
    And request <body>
    When method post
    Then status 400

    Examples:
      | case                 | body                                                                                      |
      | パスワードが短い     | { email: '#(email)', familyName: '山田', givenName: '太郎', password: 'short' }            |
      | メールアドレスの形式 | { email: 'not-an-email', familyName: '山田', givenName: '太郎', password: 'password-1' }  |
      | 氏名が空             | { email: '#(email)', familyName: '', givenName: '太郎', password: 'password-1' }           |
      | 必須項目が無い       | { email: '#(email)', password: 'password-1' }                                              |

  Scenario: 存在しないユーザーの参照とパスワード変更は 404
    Given path 'auth/v1/customers', '00000000-0000-0000-0000-000000000000'
    When method get
    Then status 404

    Given path 'auth/v1/customers', '00000000-0000-0000-0000-000000000000', 'password'
    And request { currentPassword: 'password-1', newPassword: 'password-2' }
    When method put
    Then status 404
