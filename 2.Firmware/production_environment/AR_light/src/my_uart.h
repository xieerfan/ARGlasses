
#pragma once
#define TX_PIN 1
#define RX_PIN 0

struct My_tcpdata
{
    char cmd;
    int data_len;
    char* data;
};


void send_string(char cmd,char *str);
void send_hex(char cmd,char *buff,int len);
struct My_tcpdata *receive_data();
void process_data(struct My_tcpdata *data);
void my_uart_init();
void onDataReceived();

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

