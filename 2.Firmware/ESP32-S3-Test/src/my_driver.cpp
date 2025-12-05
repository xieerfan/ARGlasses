#include "my_driver.h"
#include "XPowersLib.h"


XPowersPMU power;
#define BSP_I2C_SDA           (GPIO_NUM_15)   // SDA引脚
#define BSP_I2C_SCL           (GPIO_NUM_14)   // SCL引脚


void adcOn() {
  power.enableTemperatureMeasure();
  // Enable internal ADC detection
  power.enableBattDetection();
  power.enableVbusVoltageMeasure();
  power.enableBattVoltageMeasure();
  power.enableSystemVoltageMeasure();
}

void adcOff() {
  power.disableTemperatureMeasure();
  // Enable internal ADC detection
  power.disableBattDetection();
  power.disableVbusVoltageMeasure();
  power.disableBattVoltageMeasure();
  power.disableSystemVoltageMeasure();
}

void print_axp2101_status() { 
  String info = "";
  uint8_t charge_status = power.getChargerStatus();

  info += "power Temperature: " + String(power.getTemperature()) + "*C\n";
  info += "isCharging: " + String(power.isCharging() ? "YES" : "NO") + "\n";
  info += "isDischarge: " + String(power.isDischarge() ? "YES" : "NO") + "\n";
  info += "isStandby: " + String(power.isStandby() ? "YES" : "NO") + "\n";
  info += "isVbusIn: " + String(power.isVbusIn() ? "YES" : "NO") + "\n";
  info += "isVbusGood: " + String(power.isVbusGood() ? "YES" : "NO") + "\n";

  switch (charge_status) {
    case XPOWERS_AXP2101_CHG_TRI_STATE:
      info += "Charger Status: tri_charge\n";
      break;
    case XPOWERS_AXP2101_CHG_PRE_STATE:
      info += "Charger Status: pre_charge\n";
      break;
    case XPOWERS_AXP2101_CHG_CC_STATE:
      info += "Charger Status: constant charge\n";
      break;
    case XPOWERS_AXP2101_CHG_CV_STATE:
      info += "Charger Status: constant voltage\n";
      break;
    case XPOWERS_AXP2101_CHG_DONE_STATE:
      info += "Charger Status: charge done\n";
      break;
    case XPOWERS_AXP2101_CHG_STOP_STATE:
      info += "Charger Status: not charging\n";
      break;
  }

  info += "Battery Voltage: " + String(power.getBattVoltage()) + "mV\n";
  info += "Vbus Voltage: " + String(power.getVbusVoltage()) + "mV\n";
  info += "System Voltage: " + String(power.getSystemVoltage()) + "mV\n";

  if (power.isBatteryConnect()) {
    info += "Battery Percent: " + String(power.getBatteryPercent()) + "%\n";
  }
  Serial.println(info);
}


int my_driver_get_battery_percent() { 
  if (power.isBatteryConnect()) {
    return power.getBatteryPercent();
  }else{
    return -1;
  }
}

void my_driver_init() { 
  bool result = power.begin(Wire, AXP2101_SLAVE_ADDRESS, BSP_I2C_SDA, BSP_I2C_SCL);
  if (result == false) {
    Serial0.println("PMU is not online...");
    while (1) delay(50);
  }
  power.disableIRQ(XPOWERS_AXP2101_ALL_IRQ);
  power.setChargeTargetVoltage(3);
  // Clear all interrupt flags
  power.clearIrqStatus();
  // Enable the required interrupt function
  power.enableIRQ(
    XPOWERS_AXP2101_PKEY_SHORT_IRQ  //POWER KEY
  );
  power.setALDO1Voltage(3300);
  power.enableALDO1();
  power.setALDO2Voltage(2800);
  power.enableALDO2();
  adcOn();
}

