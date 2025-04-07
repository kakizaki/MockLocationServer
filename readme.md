

# 概要

android デバイスに仮の位置情報をセットするアプリケーションです。

* https://developer.android.com/reference/android/location/LocationManager#addTestProvider(java.lang.String,%20boolean,%20boolean,%20boolean,%20boolean,%20boolean,%20boolean,%20boolean,%20int,%20int)
* https://developers.google.com/android/reference/com/google/android/gms/location/FusedLocationProviderClient#setMockLocation(android.location.Location)
* https://developers.google.com/android/reference/com/google/android/gms/location/FusedLocationProviderClient#public-taskvoid-setmockmode-boolean-ismockmode
* https://github.com/jcraane/LocationProvider



# 主な機能

* 常駐化
* web サーバー
  * HTTP リクエストによる仮の位置情報の変更
  * テスト用の web ページの提供


# 動作説明

## 常駐化、web サーバー
常駐化のためにサービスを使用します。サービスを開始すると、8080 番ポートを使用して web サーバーを起動します。このポートに対して 位置情報を設定した HTTP リクエストを送信することで、仮の現在地情報を変更します。HTTP リクエストを送信可能であれば、PCやタブレット等の機種を問わず、ブラウザやコマンドライン等のツールを問わず、仮の現在地情報の変更ができます。

## テスト用 web ページ
簡単に試せるように Google Map を使用した web ページを提供しています。PC 等のブラウザから、本アプリを実行中の端末の IP アドレスを入力することで表示できます。


## 仮の現在地情報の設定
HTTP リクエストに指定された位置情報を仮の現在置情報として設定します。サービスを停止するまで、毎秒、実施します。(測位時刻だけは端末の現在時刻へ変更します)

この動作をしなかった場合、仮の現在地情報を最後に変更してから、ある時間が経過すると実際の GPS の位置情報が使用されます。

※ この動作の使用は選択可能です。HTTP リクエストフォーマット (後述) の repeatedlyUpdate に true をセットすることで上記の動作をします。


## サービスの終了
サービスの終了時には仮の現在地情報は削除されます。実際のセンサーが取得している位置情報を使用します。

## UML

アプリケーション起動からwebサーバーの起動まで
![image](https://github.com/user-attachments/assets/01d51d37-4e52-4dfc-bcde-37eb64812512)

仮の位置情報の登録
![image](https://github.com/user-attachments/assets/c70a58bd-480a-4dd8-9343-127a7f3e7e02)




# web サーバー

8080番ポートを使用します。

## テスト用 web ページ 

ブラウザで ```ipアドレス:8080``` へアクセスすると apk リソース内の html を表示します。

Google Map が表示され、以下の方法により位置情報を送信することができます。

* Google Map の任意の点をダブルクリックする ... クリックされた場所の位置情報を送信する
* フォームの送信ボタンをクリックする ... フォームに入力された位置情報を送信する
* マーカーをドラッグする ... ドラッグ後の位置情報を送信する


また、マップ右上にあるテキストフィールドに、```{緯度} {経度}```を入力しエンターを押すと、マップの中央を移動します。


### API キーについて
Google Map Platform の認証情報はセットしていません。セットする場合は、以下のようにクエリパラメータ ```key``` にAPIキーを渡してください。

```
ipアドレス:8080/key=?{APIキー}
```


以下、APIキーの制限の例です。
* アプリケーションの制限 ... ```HTTP リファラー``` を選択
* ウェブサイトの制限 ... ```*:8080/``` を追加
* APIの制限 ... ```キーを制限``` を選択し、```Map JavaScript API``` を選択


## APIフォーマット:位置を設定する

curl 等、リクエストを送信することで位置情報を設定します。

**エンドポイント**

```/api/location```

 
**データフォーマット**

以下の値を持つ JSON とする。

|フィールド名|値|説明|
|-|-|-|
|latitude|数値|緯度 (単位:度)|
|longitude|数値|緯度 (単位:度)|
|altitude|数値|高度 (単位:メートル)※省略した場合は 0|
|haccuracy|数値|水平精度 (単位:メートル)※省略した場合は 0|
|repeatedlyUpdate|bool|この位置情報(速度を除く)を毎秒セットする ※省略した場合は false|
|velocity|数値|速度 (単位:メートル/秒) ※省略した場合は 0|


例

```
{
  "latitude": 34.1234,
  "longitude": 134.1234,
  "altitude": 1.0,
  "haccuracy": 2.0
}
```
 

**ステータスコード**

データフォーマットが正しければ、ステータスコード 200 を返す。

データフォーマットが正しくなければ、ステータスコード 400 を返す。


