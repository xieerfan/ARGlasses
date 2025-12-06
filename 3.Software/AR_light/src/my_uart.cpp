#include "my_uart.h"
#include "Arduino.h"
#include "generated/gui_guider.h"
extern lv_ui guider_ui;

//---------------------------------发送--------------------------------------//
// 发送数据的底层函数
void _my_send(char *data, int len) {
    // 假设这里是用底层协议进行数据发送
    // send(connect_socket, data, len, 0); // 通信底层发送
    // 此处只做示例输出
    printf("发送数据: %.*s\n", len, data); // 实际发送代码替换这里
}

// 创建数据结构
struct My_tcpdata *create_data(char cmd, char *data0, int data_len) {
    struct My_tcpdata *data = (My_tcpdata *)malloc(sizeof(struct My_tcpdata));
    if (data == NULL) {
        perror("Malloc failed");
        return NULL;
    }
    data->cmd = cmd;
    data->data = data0;
    data->data_len = data_len;
    return data;
}

// 发送数据
void my_send(struct My_tcpdata *data) {
    char sendbuff[11];
    sprintf(sendbuff, "%c%08d", data->cmd, data->data_len);
    _my_send(sendbuff, strlen(sendbuff)); // 发送头
    _my_send(data->data, data->data_len); // 发送内容
    printf("发送成功: cmd:%c, data_len=%d\n", data->cmd, data->data_len);
    free(data); // 释放数据结构内存
}

// 发送字符串
void send_string(char cmd, char *str) {
    my_send(create_data(cmd, str, strlen(str)));
}

// 发送十六进制数据
void send_hex(char cmd, char *buff, int len) {
    my_send(create_data(cmd, buff, len));
}
//---------------------------------接收--------------------------------------//

// 接收数据的底层函数
int _my_receive(char *buffer, int buffer_len) {
    return Serial1.readBytes(buffer, buffer_len);
    // 假设这里是用底层协议进行数据接收
    // return recv(connect_socket, buffer, buffer_len, 0); // 实际接收代码替换这里
    // 此处只做示例模拟数据接收
}

// 准备接收数据
struct My_tcpdata * receive_data() {
    char header[9]; // cmd (1 byte) + data_len (8 bytes)
    int bytes_received = _my_receive(header, sizeof(header));

    if (bytes_received <= 0) {
        perror("接收失败或者连接关闭");
        return NULL;
    }

    // 解析命令和数据长度
    char cmd = header[0];
    int data_len = atoi(&header[1]); // 解析数据长度

    // 分配内存以接收数据
    char *data = (char *)malloc(data_len);
    if (data == NULL) {
        perror("Malloc failed");
        return NULL;
    }

    // 接收数据
    bytes_received = _my_receive(data, data_len);
    if (bytes_received <= 0) {
        free(data);
        perror("接收数据失败");
        return NULL;
    }

    // 返回数据结构
    struct My_tcpdata *received_data = (My_tcpdata *)malloc(sizeof(struct My_tcpdata));
    if (received_data == NULL) {
        free(data);
        perror("Malloc failed");
        return NULL;
    }
    received_data->cmd = cmd;
    received_data->data = data;
    received_data->data_len = data_len;

    return received_data;
}

// 处理接收到的数据
void process_data(struct My_tcpdata *data) {
    // 处理不同类型的数据
    switch (data->cmd) {
        case 'a':
            Serial.printf("接收到a字符串: %.*s\n", data->data_len, data->data);
            lv_label_set_text_fmt(guider_ui.screen_label_1, "%.*s",data->data_len,data->data);
            lv_obj_invalidate(guider_ui.screen_label_1);
            break;
        case 'b':
            Serial.printf("接收到b字符串: %.*s\n", data->data_len, data->data);
            lv_label_set_text_fmt(guider_ui.screen_label_2, "%.*s",data->data_len,data->data);
            lv_obj_invalidate(guider_ui.screen_label_2);
            break;
        case 'c':
            Serial.printf("接收到c字符串: %.*s\n", data->data_len, data->data);
            lv_label_set_text_fmt(guider_ui.screen_label_4, "%.*s",data->data_len,data->data);
            lv_obj_invalidate(guider_ui.screen_label_4);
            break;
        case 'd':
            Serial.printf("接收到d字符串: %.*s\n", data->data_len, data->data);
            lv_label_set_text_fmt(guider_ui.screen_label_5, "%.*s",data->data_len,data->data);
            lv_obj_invalidate(guider_ui.screen_label_5);
            break;
        case 'e':
            Serial.printf("接收到e字符串: %.*s\n", data->data_len, data->data);
            lv_label_set_text_fmt(guider_ui.screen_label_6, "%.*s",data->data_len,data->data);
            lv_obj_invalidate(guider_ui.screen_label_6);
            break;
        case 'f': 
            Serial.printf("接收到f字符串: %.*s\n", data->data_len, data->data);
            lv_label_set_text_fmt(guider_ui.screen_label_7, "%.*s",data->data_len,data->data);
            lv_obj_invalidate(guider_ui.screen_label_7);
            break;
        case 'g': 
            Serial.printf("接收到g字符串: %.*s\n", data->data_len, data->data);
            lv_label_set_text_fmt(guider_ui.screen_label_8, "%.*s",data->data_len,data->data);
            lv_obj_invalidate(guider_ui.screen_label_8);
            break;
        case 'h': 
            Serial.printf("接收到h字符串: %.*s\n", data->data_len, data->data);
            lv_label_set_text_fmt(guider_ui.screen_label_9, "%.*s",data->data_len,data->data);
            lv_obj_invalidate(guider_ui.screen_label_9);
            break;
        case 'i':
            Serial.printf("接收到i字符串: %.*s\n", data->data_len, data->data);
            lv_bar_set_value(guider_ui.screen_bar_1,atoi(data->data), LV_ANIM_OFF);
            lv_obj_invalidate(guider_ui.screen_bar_1);
            break;
        // 添加其他命令类型的处理
        default:
            Serial.printf("未知的命令: %c\n", data->cmd);
            break;
    }
    
    // 记得释放接收到的数据内存
    free(data->data);
    free(data);
}

void onDataReceived()
{
      // 处理接收到的数据
      while (Serial1.available())
      {
            My_tcpdata* data=receive_data();
            process_data(data);
            // 进行数据处理操作
      }
}
void my_uart_init(){
    Serial1.begin(115200,SERIAL_8N1,RX_PIN,TX_PIN);
    // Serial1.onReceive(onDataReceived);
}