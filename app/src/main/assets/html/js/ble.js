var bluetoothDevice;
var _oDevice;
var _oServer;
var _oService;
var _oCharaRead;
var _oCharaWrite;
var _descriptor;
var _oVal;
 
const BLE_PERIPHERAL_UUID = 'a791c20e-aa32-11ea-bb37-0242ac130002';
const WRITE_CHARACTERISTIC_UUID = 'c473c85e-aa32-11ea-bb37-0242ac130002';
//const RASPAI_DESC_UUID = '';
 
console.log(navigator.bluetooth);

function connect() {
    let options = {};
 
    options.filters = [
        //ここのコメントアウトを外すと周囲の全てのBLEデバイスをスキャンします。　
        //	{acceptAllDevices:true},　
        { services: [BLE_PERIPHERAL_UUID]} ,
        //{name: “raspberrypi”},
    ];

    //filterの条件で、周囲のBLEデバイスをスキャン
    navigator.bluetooth.requestDevice(options)
    .then(device => {
        console.log('device', device);
        _oDevice = device;
        //選択したデバイスと接続
        return _oDevice.gatt.connect();　　　
    })
    .then(server => {
        console.log('server', server);
        _oServer = server;
        return _oServer.getPrimaryService(BLE_PERIPHERAL_UUID);
    })
    .then(service => {
        //選択したデバイスに設定されているServiceの情報を取得
        console.log('service', service);
        _oService = service;
        return _oService.getCharacteristic(WRITE_CHARACTERISTIC_UUID);
    })
    .then(chara => {　
        //選択したServiceが保持しているCharacteristic情報を取得
        console.log('chara', chara);
        _oCharaRead = chara;
                    
        //ペリフェラルからNotify通知を受け取る準備
        /*
        chara.addEventListener('characteristicvaluechanged',onRecvSensorData);
        chara.startNotifications();
        alert('BLE端末との接続に成功しました');
        return _oCharaRead.getDescriptor(RASPAI_DESC_UUID);
        */

        alert('BLE端末との接続に成功しました');
    })
    .then(descriptor => {
        console.log('desc',descriptor);
        _descriptor = descriptor;
    })
    .catch(error => {
        console.log(error);
    });
}


//ペリフェラル側のCharacteristicの値に書き込む処理
function writeMessage(m) {
    var buffer = new TextEncoder().encode(m);
    console.log(buffer);
    
    var sendVal_log = new TextDecoder().decode(buffer);
    console.log(sendVal_log);
   
    _oCharaRead.writeValue(buffer).then(v => {
        console.log("writeValue success");
    })
    .catch(error => {
        console.log(error);
    });
}

//ペリフェラル側のCharacteristicの値にを読み込む処理
function readMessage() {　　　　　　　　　　　　　　　　　　　　　　　　　　　　　　　　　　　　　　　　　　　　　　　　　　　　　　　　　　　　　　　　　　　　　　　　　　　　　　　　　　　　　　
    _oCharaRead.readValue()
    .then(value => {
        console.log('value', value.buffer);
        _oVal = value;
        oVal = new TextDecoder('utf-8').decode(_oVal);
        //var Rcv_element = document.getElementById('Rcv_text');
        //Rcv_element.innerHTML = oVal;
    })
    .catch(error => {
        console.log(error);
    });
}

//ペリフェラル側のCharacteristicの値に追加されている説明情報を読み込む処理
function readDescriptor() {　　　　　　　　　　　　　　　　　　　　　　　　　　　　　　　　　　　　　　　　　　　　　　　　　　　　　　　　　　　　　　　　　　　　　　　　　　　　　　
    _descriptor.readValue()
    .then(value => {
        _oVal = value;
        oVal = new TextDecoder('utf-8').decode(_oVal);
        //var Desc_element = document.getElementById('Desc_text');
        //Desc_element.innerHTML = oVal;
        console.log(oVal);
    })
    .catch(error => {
        console.log(error);
    });
}

//ペリフェラル側から送られてきたNotifyを受信する処理
function onRecvSensorData(event){                                 
    console.log('Notify受信');
    let characteristic = event.target; 
    let value = characteristic.value;
    var notifVal = new TextDecoder('utf-8').decode(value);
    //var Rcv_element = document.getElementById('Rcv_text');
    //Rcv_element.innerHTML = notifVal;    
}

//BEL切断処理
function disconnect() {
    if(!_oDevice || !_oDevice.gatt.connected) return;
    _oDevice.gatt.disconnect();
    alert('BLE接続を切断しました。');
}



