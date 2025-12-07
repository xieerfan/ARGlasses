#include "my_ble.h"
#include "my_camera.h"
#include "my_sd.h"
#include "my_driver.h"
#include "my_uart.h"
#include <BLE2902.h>
#include "my_txt.h"
#include "my_es8311.h"
#define chunk_num 400


namespace BLEServerDemo
{

  bool deviceConnected = false;
  static int data_len;
  static int write_data_len;

  BLEServer *pServer = nullptr;
  BLEService *pService1 = nullptr;
  BLEService *pService2 = nullptr;
  BLEService *pService3 = nullptr;


  BLECharacteristic *pCharacteristic1_1 = nullptr;
  BLECharacteristic *pCharacteristic1_2 = nullptr;
  BLECharacteristic *pCharacteristic1_3 = nullptr;

  BLECharacteristic *pCharacteristic2_1 = nullptr;
  BLECharacteristic *pCharacteristic2_2 = nullptr;
  BLECharacteristic *pCharacteristic2_3 = nullptr;

  BLECharacteristic *pCharacteristic3_1 = nullptr;
  BLECharacteristic *pCharacteristic3_2 = nullptr;
  BLECharacteristic *pCharacteristic3_3 = nullptr;

  void send_my_data(uint8_t *data, size_t len){
    pCharacteristic3_3->setValue(data,len);
    pCharacteristic3_3->notify();
  }
  void send_my_data(std::string value){
    pCharacteristic3_3->setValue(value);
    pCharacteristic3_3->notify();
  }

  //----------------------------连接处理-------------------------//
  class ServerCallbacks : public BLEServerCallbacks
  {
    void onConnect(BLEServer *pServer)
    {
      deviceConnected = true;
      Serial.println("BLE connected");
      send_ble(true);
    }

    void onDisconnect(BLEServer *pServer)
    {
      deviceConnected = false;
      Serial.println("BLE disconnected");
      send_ble(false);
      pServer->startAdvertising(); // Restart advertising after disconnection
    }
  };

  //------------------------------------------------------------//

  //-------------------------文件接收----------------------------//

static uint8_t* json_data = nullptr;   // 最终缓冲区首地址
static size_t   json_len  = 0;         // 已用长度
static size_t   json_cap  = 0;         // 缓冲区总容量

class CharacteristicCallbacks1_2 : public BLECharacteristicCallbacks
{
    void onWrite(BLECharacteristic* pChar) 
    {
        std::string value = pChar->getValue();
        // Serial.printf("Received_1_2 Value: %s,%d\n", value.c_str(),value.length());
        if (value=="start")
        {
          Serial.printf("Received_data_name: %s\n",pCharacteristic1_3->getValue().c_str());
          start_write(pCharacteristic1_3->getValue().c_str());
          update_write(pCharacteristic1_1->getData(),pCharacteristic1_1->getValue().length());
        }else if(value=="update"){
          update_write(pCharacteristic1_1->getData(),pCharacteristic1_1->getValue().length());
        }else if(value=="end"){
          end_write();
          Serial.printf("Received_data_end: %s\n",pCharacteristic1_3->getValue().c_str());
        }
    }
};
  // -----------------------------------------------------------//
  //--------------------------照片发送-------------------------//

  class CharacteristicCallbacks2_1 : public BLECharacteristicCallbacks
  {
    void onRead(BLECharacteristic *pCharacteristic, esp_ble_gatts_cb_param_t *param)
    {
      Serial.println("read_image_len");
      pCharacteristic->setValue(data_len);
    }
  };
  class CharacteristicCallbacks2_2 : public BLECharacteristicCallbacks
  {
    void onWrite(BLECharacteristic *pCharacteristic)
    {
      std::string value = pCharacteristic->getValue();
      if (value == "getimage")
      {
        if (my_image.buf != NULL)
        {
          if (data_len > chunk_num)
          {
            // Serial.printf("Send image size: %zu bytes\n", chunk_num);
            pCharacteristic2_3->setValue(my_image.buf + write_data_len, chunk_num);
            pCharacteristic2_3->notify();
            write_data_len += chunk_num;
            data_len -= chunk_num;
          }
          else
          {
            // Serial.printf("Send image size: %zu bytes\n", data_len);
            pCharacteristic2_3->setValue(my_image.buf + write_data_len, data_len);
            pCharacteristic2_3->notify();
            write_data_len = 0;
            data_len = 0;
            send_my_data("image_end");
          }
        }
      }else if (value == "takeimage")
      {
        data_len = my_image.len;
        write_data_len = 0;
      }
    }
  };


  //------------------------------------------------------------//

  int nowpage = 0;
  int nowmode = 0;
  int nowthing=0;
  char nowname[256] = {0};


  //---------------------------数据获取--------------------------//
    class CharacteristicCallbacks3_1 : public BLECharacteristicCallbacks
  {
    void onRead(BLECharacteristic *pCharacteristic, esp_ble_gatts_cb_param_t *param)
    {
      Serial.println("read_battery_percent");
      char nowbattery[10];
      sprintf(nowbattery,"%d",my_driver_get_battery_percent());
      pCharacteristic->setValue(nowbattery);
    }
  };
    class CharacteristicCallbacks3_2 : public BLECharacteristicCallbacks
  {
    void onWrite(BLECharacteristic *pCharacteristic)
    {
      std::string value = pCharacteristic->getValue();
      Serial.printf("Received_data: %s\n",value.c_str());
      if(value=="display_txt"){
        nowpage=0;
        nowmode=1;
        sprintf(nowname,"%s",pCharacteristic1_3->getData()); 
        nowthing=1;
      }else if(value=="display_json"){
        nowpage=0;
        nowmode=2;
        sprintf(nowname,"%s",pCharacteristic1_3->getData()); 
        nowthing=2;
      }else if(value=="next_page"){
        nowpage++;
        if(nowmode==1){
          nowthing=1;
        }else if(nowmode==2){
          nowthing=2;
        }
      }else if(value=="pre_page"){
        nowpage--;
        if(nowpage<0){
          nowpage=0;
        }
        if(nowmode==1){
          nowthing=1;
        }else if(nowmode==2){
          nowthing=2;
        }
      }else if(value=="play_mp3"){
        sprintf(nowname,"%s",pCharacteristic1_3->getData()); 
        nowthing=3;
      }else if(value=="stop_mp3"){
        nowthing=4;
      }else if(value=="delete_json"){
        nowthing=5;
      }else if(value=="ota_updata"){
        nowthing=6;
      }else if(value=="vol_up"){
        vol_up();
      }else if(value=="vol_down"){
        vol_down();
      }else if(value=="delete_file"){
        sprintf(nowname,"%s",pCharacteristic1_3->getData());
        delete_file(nowname);
      }
    }
  };
  //------------------------------------------------------------//
  void my_ble_init()
  {
    BLEDevice::init("AR_GLASS");
    pServer = BLEDevice::createServer();
    pServer->setCallbacks(new ServerCallbacks());

    pService1 = pServer->createService(BLEUUID("aabb0100-0000-1000-8000-00805f9b34fb"));
    pService2 = pServer->createService(BLEUUID("aabb0200-0000-1000-8000-00805f9b34fb"));
    pService3 = pServer->createService(BLEUUID("aabb0300-0000-1000-8000-00805f9b34fb"));


    //-------------------------文件接收----------------------------//
    pCharacteristic1_1 = pService1->createCharacteristic(
        BLEUUID("aabb0101-0000-1000-8000-00805f9b34fb"),
        BLECharacteristic::PROPERTY_WRITE);//数据

    pCharacteristic1_2 = pService1->createCharacteristic(
        BLEUUID("aabb0102-0000-1000-8000-00805f9b34fb"),
        BLECharacteristic::PROPERTY_WRITE);//命令
    pCharacteristic1_2->setCallbacks(new CharacteristicCallbacks1_2());

    pCharacteristic1_3 = pService1->createCharacteristic(
        BLEUUID("aabb0103-0000-1000-8000-00805f9b34fb"),
        BLECharacteristic::PROPERTY_WRITE);//名字

    //-----------------------------------------------------------//

    //--------------------------照片发送-------------------------//
    pCharacteristic2_1 = pService2->createCharacteristic(
        BLEUUID("aabb0201-0000-1000-8000-00805f9b34fb"),
        BLECharacteristic::PROPERTY_READ);//长度
    pCharacteristic2_1->setCallbacks(new CharacteristicCallbacks2_1());

    pCharacteristic2_2 = pService2->createCharacteristic(
        BLEUUID("aabb0202-0000-1000-8000-00805f9b34fb"),
        BLECharacteristic::PROPERTY_WRITE);//命令更新
    pCharacteristic2_2->setCallbacks(new CharacteristicCallbacks2_2());

    pCharacteristic2_3 = pService2->createCharacteristic(
        BLEUUID("aabb0203-0000-1000-8000-00805f9b34fb"),
        BLECharacteristic::PROPERTY_NOTIFY);//接收数据
    pCharacteristic2_3->addDescriptor(new BLE2902());

    //------------------------------------------------------------//

    //---------------------------数据获取--------------------------//
    pCharacteristic3_1 = pService3->createCharacteristic(
        BLEUUID("aabb0301-0000-1000-8000-00805f9b34fb"),//电量
        BLECharacteristic::PROPERTY_READ);
    pCharacteristic3_1->setCallbacks(new CharacteristicCallbacks3_1());

    pCharacteristic3_2 = pService3->createCharacteristic(
        BLEUUID("aabb0302-0000-1000-8000-00805f9b34fb"),
        BLECharacteristic::PROPERTY_WRITE);//控制命令
    pCharacteristic3_2->setCallbacks(new CharacteristicCallbacks3_2());

    pCharacteristic3_3 = pService3->createCharacteristic(
        BLEUUID("aabb0303-0000-1000-8000-00805f9b34fb"),
        BLECharacteristic::PROPERTY_NOTIFY);//通知
    pCharacteristic3_3->addDescriptor(new BLE2902()); 
    //------------------------------------------------------------//

    pService1->start();
    pService2->start();
    pService3->start();
    pServer->getAdvertising()->start();
    Serial.println("Waiting for client connection...");
  }

}