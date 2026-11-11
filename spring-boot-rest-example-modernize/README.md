# Java 17 Hotel JAR / Mule Runtime 4.12.0

既存のホテルAPIから、業務処理とPostgreSQLアクセスを通常のJavaライブラリJARに移したプロジェクトです。
HTTPはMule HTTP Listener、Java呼び出しはJava Module、API管理はAPI Managerが担当します。
元の `../spring-boot-rest-example` は変更しません。

## 構成と移行範囲

```text
HTTP client
  → Mule HTTP Listener / API Manager policies
  → Mule Flow (HTTP routing, JSON/XML, status/headers)
  → Java Module
  → HotelApplication (one instance per Mule app)
  → HotelService / Spring Data JPA / Hibernate / HikariCP
  → PostgreSQL
```

| 旧実装 | 新実装 |
| --- | --- |
| Spring MVC Controller、Servlet、Tomcat | Mule HTTP Listener / Flow |
| HotelService、HotelRepository | JAR内に保持。Spring/JPA APIを更新 |
| javax.persistence | jakarta.persistence |
| Hotel entity | 同じテーブル・カラム、既存のhibernate_sequenceを使用 |
| Controller内の存在確認・更新ID照合 | JARのサービスに移動 |
| REST呼び出し前のAOPログ | サービスメソッド内のSLF4Jログ |
| 大きいページ取得のカウンター | JARのmetrics()。同じメトリクス名、size > 50の条件を維持 |
| HotelServiceHealth / service name | JARのhealth()とHOTEL_SERVICE_NAME。実DBの接続状態も返す |
| Springの例外→HTTP応答 | 業務例外はJAR、HTTP 400/404/500への変換はMule |
| Jackson/JAXBのHTTPシリアライズ | Mule DataWeave |
| Swagger UI | RAML仕様をExchange/API Managerで管理 |
| ActuatorのJVM・環境・HTTP管理エンドポイント | Mule Runtime / Anypoint Monitoring側で管理 |
| 使用されていないHotelServiceEvent | 呼び出し元がないため移植対象外 |

業務JARはHTTPポートを開きません。`java -jar`で起動するBoot実行可能JARではなく、クラスがJAR直下に配置された通常のJARです。
Spring Bootは親POMによる依存・ビルド管理だけに使用し、Boot本体・自動構成・Web Starter・repackageは使用しません。
H2、Spring Security、Springfox、Servlet/Tomcat、アプリ独自のJSON/XMLライブラリ、ログ実装はJARの実行時依存から除外しました。
Hibernateが内部で必要とするJakarta XML Bindingなどの推移的依存は残ります。

## 前提とバージョン

- JDK 17、Maven 3.6.3以上。Java 8/26ではビルドを拒否します。
- Mule Runtime **4.12.0**、HTTP Connector 1.11.3、Java Module 2.0.8。
- Mule側のSpring Module 2.1.4は、アプリ単位のインスタンス作成・破棄のために使用します。
- Spring Moduleの型定義に必要なspring-security-core 6.5.11はMuleアプリ側だけに追加しています。業務JARには含めず、認証フィルターも設定していません。
- 業務JAR: Spring Boot BOM 3.5.16（Spring Framework 6.2.19、Spring Data JPA 3.5系、Hibernate 6.6.53.Final、HikariCP 6.3.3）。
- PostgreSQL JDBC 42.7.14を明示指定。PostgreSQL 18.6で検証済みです。

Muleのクラスローダーからも読めるよう、Java Moduleの公開メソッドにはJDKのMap/List/数値のみを使用します。
JPA Entity、Hibernate Proxy、Spring PageをMule境界へ渡しません。
`-parameters`を有効にし、Flowでは引数名を使っています。

## ビルド

PowerShellで、JDK 17のインストール先を設定してください。このPCではAnypoint Code Builder同梱JDKも利用できます。

```powershell
$env:JAVA_HOME = 'C:\Users\onoue1\AnypointCodeBuilder\java\jdk-17.0.20.1+1'
$env:Path = "$env:JAVA_HOME\bin;$env:Path"
Set-Location 'D:\git\tailup7\hotel-postgresql\spring-boot-rest-example-modernize'
java -version
mvn -version
mvn clean install
```

成果物: `target/hotel-service-java17-1.0.0.jar`

`install`はJARとPOMをローカルMavenリポジトリに登録します。MuleプロジェクトはそのPOMから推移的依存も解決します。
JARファイルだけをMuleのフォルダーへコピーしても依存関係は解決されません。
別PCやCIではJARとPOMを社内Mavenリポジトリに登録し、Mule側から参照してください。

## PostgreSQLと既存データ

既存の`bootexample`をそのまま利用できます。新規の場合は管理者としてユーザーとDBを作成してください。

```sql
CREATE USER bootexample;
\password bootexample
CREATE DATABASE bootexample OWNER bootexample;
```

旧・新アプリを停止してから、対象DBのバックアップを取得したうえで以下を実行します。

```powershell
psql -h localhost -p 5432 -U bootexample -d bootexample -v ON_ERROR_STOP=1 -f db/schema.sql
```

スクリプトは既存のホテル行を維持し、テーブル・シーケンスがなければ作成します。
既存行の最大IDとシーケンスの現在値を比較し、採番値を後退させずに調整します。
`@SequenceGenerator(allocationSize=1)`により、Hibernate 6の既定のシーケンス名・採番間隔への変更を避けています。
旧アプリと並行してスキーマ移行しないでください。

| 環境変数 | 既定値 |
| --- | --- |
| DB_URL | jdbc:postgresql://localhost:5432/bootexample |
| DB_USERNAME | bootexample |
| DB_PASSWORD | 必須 |
| DB_SCHEMA_MODE | validate |
| DB_POOL_SIZE | 10（1〜100） |
| HOTEL_SERVICE_NAME | default profile: |

`validate`が既定です。JAR起動時にテーブルを削除・再作成しません。`update`は開発時に明示指定した場合のみ使用できます。
本実装はJAR内のローカルJPAトランザクションを使い、MuleのXAトランザクションには参加しません。
複数のJava呼び出しを一つのトランザクションにしたい場合は、JAR内に一つの業務メソッドとして実装してください。

## Java API

呼び出しクラス: `com.khoubyari.example.api.HotelApplication`

| メソッド | 引数 | 戻り値 |
| --- | --- | --- |
| createHotel | Map hotel | 作成済みホテルのMap（id付き） |
| getHotel | long id | ホテルのMap |
| getAllHotels | Integer page, Integer size | contentとページ情報のMap |
| updateHotel | long id, Map hotel | void |
| deleteHotel | long id | void |
| health | なし | status、database、hotelService情報 |
| metrics | なし | メトリクス名と値 |
| close | なし | コンテキスト・接続プールを解放 |

コンストラクターは引数なし（環境変数）、設定Map、DatabaseSettingsの3種類です。
環境変数が使えない配備方式では設定Mapを渡してください。MuleのSecure Configuration Propertiesで復号した値をMapに渡す構成にもできます。
アプリ起動時に1回だけ作成して共有し、停止時に`close()`を呼びます。
`mule-example/src/main/resources/beans.xml`でsingletonと`destroy-method="close"`を指定済みです。
HTTPリクエストごとの`java:new`は使用しません。

従来と同じ既定値はpage=0、size=100、rating=0です。更新ではパスIDと本文IDが一致する必要があります。
不正な整数・長すぎる文字列はDB処理前に業務例外にします。
POSTに既存IDを渡して更新できてしまう旧実装の挙動は廃止し、id省略または0のみ受け付けます。
一覧はid昇順で安定した順序にしました。IDはJava long（JSONでは数値）です。

## Muleアプリの起動

1. 上記`mvn clean install`を実行します。
2. Anypoint Studioに`mule-example`をMaven/Muleプロジェクトとしてインポートし、Runtime 4.12.0とJDK 17を選択します。
3. Run ConfigurationのEnvironmentにDB接続情報を設定します。PowerShellで設定した環境変数は、既に起動しているStudioには反映されません。Anypoint Studio上で、Muleアプリ用の起動設定に環境変数を追加します。
  + Studioの「Package Explorer」で `hotel-mule-api` プロジェクトを右クリックします。
  + `Run As` → `Run Configurations...` を開きます。
  + 左側の `Mule Application` から、このプロジェクトの起動設定を選択します。- まだない場合は、左上の「New launch configuration」アイコンから作成します。
  + `Environment` タブを開きます。
  + `New...` を押し、以下を1件ずつ追加します。

  | Name | Value |
  | ---- | ----- |
  | `DB_URL`| `jdbc:postgresql://localhost:5432/bootexample` |
  | `DB_USERNAME` | `bootexample` |
  | `DB_PASSWORD` | `PostgreSQLで設定したパスワード` |
  | `DB_SCHEMA_MODE` | `validate` |
  | `DB_POOL_SIZE` | `10` |
  | `HOTEL_SERVICE_NAME` | `local-studio` |
4. Muleアプリを起動します。スタンドアロンへ配備する場合は下記でMule配備用アーカイブを作成してください。

```powershell
$env:DB_URL = 'jdbc:postgresql://localhost:5432/bootexample'
$env:DB_USERNAME = 'bootexample'
$env:DB_PASSWORD = '<設定したパスワード>'
mvn -f mule-example/pom.xml clean package
```

Mule配備用成果物: `mule-example/target/hotel-mule-api-1.0.0-mule-application.jar`

`mule-example/pom.xml`の`sharedLibraries`によって、業務JARをJava/Spring Moduleから参照可能にします。
業務JARのJPAコンテキスト初期化・実行時はライブラリのクラスローダーを使用し、終了時に元のスレッドのクラスローダーへ戻します。
Mule側は既存URLの`/example/v1/hotels`と`/example/v1/hotels/{id}`を提供します。
作成時は201と相対URIのLocation、更新・削除は204、未存在は404、入力エラーは400です。
JSONと`<hotel>...</hotel>`形式のXML本文を受け付けます。XML応答はAccept: application/xmlで選択します。

ヘルスチェックと業務メトリクスはMule側で以下のURLに公開します（従来の8091番ポートは起動しません）。

- `GET /example/v1/health`
- `GET /example/v1/metrics`

## 動作確認
Powershellから以下を実行
``` powershell
Invoke-RestMethod 'http://localhost:8090/example/v1/health'
```

## API Manager

JARをAPI Managerへ直接登録するのではなく、HTTP Listenerを持つMuleアプリをAPIインスタンスに関連付けます。

1. `mule-example/src/main/resources/api/hotels.raml`をExchangeへAPI仕様として公開します。
2. API Managerでその仕様からAPIインスタンスを作成し、Mule Gateway / Basic Endpointを選択します。
3. API Instance IDを取得し、Runtime設定に`api.instance.id`として設定します。
4. `mule-example/api-manager/autodiscovery.xml`を`mule-example/src/main/mule/`へコピーして再ビルドします。`flowRef="hotel-api-main"`がHTTP Listenerのある入口です。
5. 配備先に応じたAnypoint Platform認証情報をRuntimeへ設定します（環境の`anypoint.platform.client_id` / `anypoint.platform.client_secret`など）。値はソースへ保存しません。
6. 配備後、API Managerの接続状態とAnalyticsを確認し、Client ID EnforcementやRate Limitingを設定して実リクエストで検証します。

API Manager用XMLはローカル起動で認証情報を要求しないよう、標準の読み込み対象から外しています。
API Manager連携を有効にする際は上記コピー操作が必要です。
RAMLはAPI仕様です。サンプルFlowはJava Module呼び出しを明示するため手動ルーティングで、APIkitによる仕様の自動検証は行いません。
TLS、利用者認証、流量制限、運用メトリクスの公開範囲はMule/Gateway側で設定します。

## テスト

通常の`mvn test`はDB不要の単体テストです。DBを用いた検証は明示的に`postgres-it`プロファイルで実行し、接続情報がなければ失敗します。

```sql
CREATE USER bootexample_test;
\password bootexample_test
CREATE DATABASE bootexample_test OWNER bootexample_test;
```

```powershell
psql -h localhost -U bootexample_test -d bootexample_test -v ON_ERROR_STOP=1 -f db/schema.sql
$env:TEST_DB_URL = 'jdbc:postgresql://localhost:5432/bootexample_test'
$env:TEST_DB_USERNAME = 'bootexample_test'
$env:TEST_DB_PASSWORD = '<テスト用パスワード>'
mvn -Ppostgres-it verify
```

統合テストは実際にコミットし、別のJDBC接続で永続化を確認します。
終了時にはテスト自身が作成したIDの行だけを削除します。テーブル・既存データは削除しません。必ずテスト専用DBを指定してください。
CRUD、再初期化、ID採番、ID不一致・未存在・ページ入力、メトリクス、並行実行、クラスローダー復元、closeを検証します。

同じテスト専用DBを使用して、MUnit 3.7.1からMule 4.12.0のHTTP Listener / Java Moduleを実行するテストも用意しています。
通常のMuleパッケージ作成では実行せず、次のプロファイルで明示的に実行します。

```powershell
mvn -f mule-example/pom.xml -Pmule-it test
```

`TEST_DB_*`をMUnitプロセスの`DB_*`へ渡し、HTTPポートは空きポートを自動選択します。
MUnitはMule Kernel 4.12.0でFlowと実DBを検証します。API Gatewayのポリシー検証はこのテストには含みません。
HTTPテストは作成したホテルを正常終了時に削除します。途中で失敗した場合はテスト行が残ることがあるため、こちらも必ずテスト専用DBを使用してください。

Muleアプリ起動後のHTTP検証:

```powershell
./scripts/smoke-mule.ps1
# 別ポートの場合
./scripts/smoke-mule.ps1 -BaseUrl 'http://localhost:18090/example/v1'
```

API Managerの認証・ポリシー適用は、対象Anypoint組織へ配備した後に別途確認してください。

## 今回の確認結果（2026-11-11）

JDK 17.0.20.1、Maven 3.9.16、既存DBと分離したPostgreSQL 18.6を使用しました。

| 確認 | 結果 |
| --- | --- |
| JARの単体テスト | 4件成功 |
| 実PostgreSQLの結合テスト | 5件成功。CRUD、コミット、再初期化、並行実行、終了処理など |
| MUnit / Mule Kernel 4.12.0 | 2シナリオ成功。実HTTP Listener → Java Module → PostgreSQLを通して検証 |
| HTTP形式・例外 | JSON/XML、201/204/400/404/405/415、Location、ID不一致、不正JSON、XMLの業務エラーを確認 |
| 接続プール | Muleアプリ停止時のHikariCP正常終了を確認 |
| DB移行SQL | 2回実行して既存行の保持とシーケンスの調整を確認 |
| RAML | RAML 1.0パーサーで検証済み |
| パッケージ | Java 17通常JARとMule配備用アーカイブを生成 |

Studio同梱のEE Runtimeを単体で起動する確認は、ローカルのライセンス検証エラーにより完了していません。
上記のMule実行確認はMUnitのKernel 4.12.0によるものです。対象のEE環境への配備、およびAPI Managerの接続・ポリシー適用は未確認です。

## 公式資料

- [Mule Runtime 4.12.0 release notes](https://docs.mulesoft.com/release-notes/mule-runtime/mule-4.12.0-release-notes)
- [Mule Java support](https://docs.mulesoft.com/general/java-support)
- [Java Module invocation / -parameters](https://docs.mulesoft.com/java-module/latest/java-invoke-method)
- [Java Module 2.0.8 compatibility](https://docs.mulesoft.com/release-notes/connector/java-module)
- [Spring Module lifecycle configuration](https://docs.mulesoft.com/spring-module/latest/)
- [Shared libraries](https://docs.mulesoft.com/mule-runtime/4.12/mmp-concept#configure-shared-libraries)
- [API Autodiscovery](https://docs.mulesoft.com/mule-gateway/mule-gateway-config-autodiscovery-mule4)
- [Spring Boot 3.5 / Java 17 requirements](https://docs.spring.io/spring-boot/3.5/system-requirements.html)
