# Hotel API（すべてMule Flow版）

`spring-boot-rest-example` のホテルAPIを、Javaコードを呼び出さない完全なMuleアプリへ移行したプロジェクトです。想定実行環境は **Mule Runtime 4.12.0 / Java 17 / PostgreSQL** です。

Spring Boot、Spring MVC、Spring Data JPA、Hibernate、Jackson、組み込みTomcat、Java Module、Spring Module、H2は使用していません。HTTP、ルーティング、入力検証、SQL、JSON/XML変換、エラー処理、ヘルスチェック、メトリクスをMule Flow、Database Connector、Object Store Connector、DataWeaveで実装しています。

## 実装概要

処理の対応は次のとおりです。

| 元の実装 | Mule版 |
|---|---|
| Spring MVC / Tomcat | HTTP Listenerと`hotel-api-main` Flow |
| `HotelController` | Main FlowのChoiceルーティング |
| `HotelService` | CRUD用Subflow |
| Spring Data JPA / Hibernate | Database Connectorのパラメーター化SQL |
| Jackson / JAXB | DataWeaveによるJSON/XML入出力 |
| `@ExceptionHandler` | Flow Error Handler |
| Actuatorの独自HealthIndicator | `/example/v1/health` Subflow |
| CounterService | 一時Object Storeと`/example/v1/metrics` |
| Swagger | `src/main/resources/api/hotels.raml` |

主要ファイルは次のとおりです。

- `src/main/mule/hotel-api.xml`: HTTP、CRUD、SQL、変換、エラー処理を含む全Flow
- `src/main/resources/config.properties`: ListenerとDB接続のプロパティ
- `src/main/resources/api/hotels.raml`: API仕様
- `src/test/munit/hotel-http-suite.xml`: 実PostgreSQLを使用するHTTP統合テスト
- `db/schema.sql`: 手動でスキーマを作る場合のSQL
- `scripts/smoke-test.ps1`: 起動済みアプリに対するスモークテスト
- `api-manager/autodiscovery.xml`: API Managerを有効化する場合のテンプレート

## API

| Method | Path | 正常応答 |
|---|---|---|
| POST | `/example/v1/hotels` | `201`、`Location`ヘッダー |
| GET | `/example/v1/hotels?page=0&size=100` | `200`、ページ形式 |
| GET | `/example/v1/hotels/{id}` | `200` |
| PUT | `/example/v1/hotels/{id}` | `204` |
| DELETE | `/example/v1/hotels/{id}` | `204` |
| GET | `/example/v1/health` | DB接続時`200`、切断時`503` |
| GET | `/example/v1/metrics` | `200` |

リクエストの`Content-Type`とレスポンスの`Accept`には`application/json`または`application/xml`を使用できます。GETの`Accept`を省略した場合はJSONです。

`GET /hotels`で`size > 50`を指定すると、元のコードと同じ名前の`Khoubyari.HotelService.getAll.largePayload`カウンターが増えます。このカウンターはアプリ内の一時Object Storeにあり、Muleアプリを再起動すると0へ戻ります。

## 前提条件

- Anypoint Studio 7（Mule Runtime 4.12.0を利用できること）
- Java 17
- Maven 3.9系
- PostgreSQL
- 接続先データベースと、そのスキーマにテーブル・シーケンスを作成できるDBユーザー

例として、PostgreSQLの管理ユーザーで次を実行します。既に`bootexample`がある場合は不要です。

```powershell
$env:PGPASSWORD = '<PostgreSQL管理ユーザーのパスワード>'
& 'C:\Program Files\PostgreSQL\18\bin\psql.exe' -U postgres -c "CREATE USER bootexample WITH PASSWORD 'bootexample';"
& 'C:\Program Files\PostgreSQL\18\bin\createdb.exe' -U postgres -O bootexample bootexample
```

最初のCRUDリクエスト時に、Flowが`hibernate_sequence`と`hotel`を`CREATE ... IF NOT EXISTS`で作成します。DBユーザーにDDL権限を与えない運用では、先に管理ユーザーで次を実行し、`bootexample`へテーブルとシーケンスの権限を付与してください。

```powershell
$env:PGPASSWORD = '<パスワード>'
& 'C:\Program Files\PostgreSQL\18\bin\psql.exe' `
  -h localhost -U bootexample -d bootexample `
  -f '.\db\schema.sql'
```

## Anypoint Studioで起動する

1. Studioで `File` > `Import` を開き、Mule/Anypoint Studioプロジェクトのインポートを選びます。
2. この`spring-boot-rest-example-all-muleflow`フォルダーを指定します。
3. Project Runtimeが`Mule Server 4.12.0`、JREがJava 17であることを確認します。
4. `Run` > `Run Configurations...` > `Mule Application`から本プロジェクトの起動設定を開きます。
5. `Environment`タブに次の3項目を追加します。

| Name | Value例 |
|---|---|
| `DB_URL` | `jdbc:postgresql://localhost:5432/bootexample` |
| `DB_USERNAME` | `bootexample` |
| `DB_PASSWORD` | `bootexample` |

6. `Apply`、`Run`の順に押します。
7. Consoleにアプリの開始完了が表示されたら、PowerShellから確認します。

PowerShellで設定済みの環境変数は、既に起動しているStudioへは自動反映されません。そのため、上記のRun Configurationへ設定するか、環境変数設定後にStudioを起動し直してください。

8090番ポートを別のアプリが使用している場合は、`src/main/resources/config.properties`の`http.port`を変更します。

## 手動の動作確認

以下はすべて **コマンドプロンプトではなくPowerShell** で実行します。

### 1. ヘルスチェック

```powershell
Invoke-RestMethod 'http://localhost:8090/example/v1/health'
```

期待値は`status = UP`、`database = PostgreSQL`です。

### 2. JSONで作成

```powershell
$body = @{
  name = 'Tokyo Hotel'
  description = 'Mule Flow sample'
  city = 'Tokyo'
  rating = 4
} | ConvertTo-Json

$created = Invoke-WebRequest `
  -Uri 'http://localhost:8090/example/v1/hotels' `
  -Method Post `
  -ContentType 'application/json' `
  -Body $body

$created.StatusCode
$created.Headers['Location']
```

期待値は`201`と、`/example/v1/hotels/{id}`で終わる`Location`です。

### 3. 取得、更新、削除

```powershell
$location = $created.Headers['Location']
$id = [long]($location -split '/')[-1]
$uri = "http://localhost:8090/example/v1/hotels/$id"

Invoke-RestMethod $uri

$update = @{
  id = $id
  name = 'Tokyo Hotel Updated'
  description = 'updated'
  city = 'Tokyo'
  rating = 5
} | ConvertTo-Json

Invoke-WebRequest -Uri $uri -Method Put -ContentType 'application/json' -Body $update
Invoke-WebRequest -Uri $uri -Method Delete
```

PUTとDELETEの期待値は`204`です。削除後に同じURIをGETすると`404`になります。

### 4. XML応答

```powershell
Invoke-RestMethod `
  -Uri 'http://localhost:8090/example/v1/hotels?page=0&size=100' `
  -Headers @{ Accept = 'application/xml' }
```

### 5. 一括スモークテスト

```powershell
.\scripts\smoke-test.ps1
```

このスクリプトはHealth、POST、GET、PUT、一覧、Metrics、DELETEを確認し、作成したデータを最後に削除します。

## Mavenでビルドする

PowerShellの現在ディレクトリをこのプロジェクトのルート、つまり`pom.xml`がある場所へ移動してから実行します。

```powershell
$env:JAVA_HOME = 'C:\Users\onoue1\AnypointCodeBuilder\java\jdk-17.0.20.1+1'
$env:Path = "$env:JAVA_HOME\bin;$env:Path"

mvn clean package
```

生成物は次のファイルです。

```text
target/hotel-all-muleflow-1.0.0-mule-application.jar
```

これはJavaの実行可能JARではなく、Mule RuntimeへデプロイするMuleアプリケーションJARです。

## MUnitを実行する

MUnitはMule Runtime 4.12.0を起動し、実際のPostgreSQLに対してHTTP経由でJSON/XML CRUD、ページング、Health、Metrics、400/404/405/415を確認します。

```powershell
$env:JAVA_HOME = 'C:\Users\onoue1\AnypointCodeBuilder\java\jdk-17.0.20.1+1'
$env:Path = "$env:JAVA_HOME\bin;$env:Path"
$env:TEST_DB_URL = 'jdbc:postgresql://localhost:5432/bootexample'
$env:TEST_DB_USERNAME = 'bootexample'
$env:TEST_DB_PASSWORD = 'bootexample'

mvn -Pmule-it test
```

テスト中のHTTPポートはMUnitが動的に割り当てるため、Studioで8090番の別アプリが動いていても衝突しません。

## API Managerへ関連付ける

1. `src/main/resources/api/hotels.raml`をExchangeへ公開します。
2. API ManagerでAPI Instanceを作成し、API Instance IDを確認します。
3. `api-manager/autodiscovery.xml`を`src/main/mule/autodiscovery.xml`としてコピーします。
4. StudioのRun Configurationまたはデプロイ先プロパティに`api.instance.id=<API Instance ID>`を設定します。
5. アプリを再ビルド・再デプロイします。

Autodiscoveryの`flowRef`は全HTTP要求の入口である`hotel-api-main`です。そのため、この入口へAPI Managerのポリシーを適用できます。API Managerを設定していないローカル実行で起動エラーを発生させないように、Autodiscovery XMLは初期状態ではテンプレートディレクトリに置いてあります。

## 元アプリとの運用上の差

- Spring Boot Actuatorの全エンドポイントを複製するのではなく、アプリ固有のHealthとMetricsをAPI配下へ実装しています。Mule Runtime自体の監視はRuntime Managerを使用します。
- Swagger UIの代わりにRAMLを同梱しています。Exchange/API Managerへ公開できます。
- 元の管理ポート8091は使用せず、API Managerで一元管理しやすいように8090のHTTP ListenerへHealthとMetricsも公開しています。
- 一覧はID昇順で安定して返します。
- SQL値はすべてDatabase Connectorの入力パラメーターで渡し、文字列連結によるSQLインジェクションを避けています。
- DBパスワードはGit管理ファイルへ書かず、環境変数から受け取ります。
