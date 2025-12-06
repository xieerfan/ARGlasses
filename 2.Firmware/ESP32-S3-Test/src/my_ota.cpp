#include "my_ota.h"
#include <Update.h>
#include "SD_MMC.h"

static void rebootEspWithReason(String reason) {
  Serial.println(reason);
  delay(1000);
  ESP.restart();
}
void performUpdate(Stream &updateSource, size_t updateSize) {
  String result = String((char)0x0F); // 状态前缀
  if (Update.begin(updateSize)) {
    size_t written = Update.writeStream(updateSource);
    result += "已写入 " + String(written) + "/" + String(updateSize)
           + " [" + String(written * 100 / updateSize) + "%]\n";
    if (Update.end() && Update.isFinished()) {
      result += "升级成功，即将重启\n";
    } else {
      result += "升级失败，错误码：" + String(Update.getError()) + "\n";
    }
  } else {
    result += "空间不足，无法开始 OTA\n";
  }
  Serial.println(result);
  // if (deviceConnected) {
  //   pCharacteristicTX->setValue(result.c_str());
  //   pCharacteristicTX->notify();
  //   delay(5000);
  // }
}
void updateFromSD() {
  File updateBin = SD_MMC.open("/update.bin");
  if (updateBin) {
    if (updateBin.isDirectory()) {
      Serial.println("Error, update.bin is not a file");
      updateBin.close();
      return;
    }

    size_t updateSize = updateBin.size();

    if (updateSize > 0) {
      Serial.println("Trying to start update");
      performUpdate(updateBin, updateSize);
    }
    else {
      Serial.println("Error, file is empty");
    }

    updateBin.close();

    // when finished remove the binary from spiffs to indicate end of the process
    Serial.println("Removing update file");
    SD_MMC.remove("/update.bin");

    rebootEspWithReason("Rebooting to complete OTA update");
  }
  else {
    Serial.println("Could not load update.bin from sdcard root");
  }
}