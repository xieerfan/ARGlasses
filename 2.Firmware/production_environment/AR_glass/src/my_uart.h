#ifndef ___MY_UART_H_
#define ___MY_UART_H_

#define UART_RX 42
#define UART_TX 41
#include "Arduino.h"

struct My_tcpdata
{
    char cmd;
    int data_len;
    char* data;
};

void my_uart_init();
void send_string(char cmd,char *str);
void send_hex(char cmd,char *buff,int len);



//-----------------自定义通讯协议---------------------------//
//cmd            1 byte          命令位
//data_len       8 byte          长度位
//data           data_len byte   数据位  
//---------------------命令表------------------------------//        

void send_content(char *str);
void send_time(char *str);
void send_date(char *str);
void send_name(char *str);
void send_pages(char *str);
void send_ble(bool isopen);
void send_battery(int num);
void send_bar(int num);
void send_bottom(char *str);
#endif
