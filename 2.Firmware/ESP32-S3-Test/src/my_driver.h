#ifndef __MY_DRIVER_H
#define __MY_DRIVER_H


#define XPOWERS_CHIP_AXP2101

#include "Arduino.h"
void my_driver_init();
void print_axp2101_status();
int my_driver_get_battery_percent();

#endif
