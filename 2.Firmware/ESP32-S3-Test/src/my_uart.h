#ifndef ___MY_UART_H_
#define ___MY_UART_H_

#define UART_RX 43
#define UART_TX 44
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
//               a            string 字符串
//               b            hex    文件
//               c            RGB565 图片
//               d            jpg    图片
//               e            png    图片
//               f            time   时间
//               g            json   文本
//               h            txt    文本

#endif
