#include "my_uart.h"

void onDataReceived()
{
      // 处理接收到的数据
      char data[9];
      while (Serial1.available())
      {
            Serial1.readBytes(data,9);
            char cmd = data[0];
            int data_len = atoi(data + 1);
            ESP_LOGI(TAG, "接收成功:cmd:%c,data_len=%d ", cmd, data_len);
            char *newdata = (char *)malloc(data_len);
            if (data == NULL)
            {
                Serial0.printf("malloc failed");
                return;
            }
            Serial1.readBytes(newdata,data_len);
            switch (cmd)
            {
            case 'a':
                /* code */
                newdata[data_len]='\0';
                Serial0.printf( "接收string:%s", newdata);
                break;
            case 'b':
                /* code */
                break;
            case 'c':
                /* code */
                break;
            case 'd':
                /* code */
                break;
            case 'e':
                /* code */
                break;
            case 'f':
                /* code */
                break;
            case 'g':
                /* code */
                newdata[data_len]='\0';
                Serial0.printf("接收json:%s", newdata);
                break;
            case 'h':
                /* code */
                newdata[data_len]='\0';
                Serial0.printf("接收txt:%s", newdata);
                break;

            default:
                break;
            }
            free(newdata);
        }
}


void my_uart_init(){
    Serial1.begin(115200,SERIAL_8N1,UART_RX,UART_TX);
    Serial1.onReceive(onDataReceived);
}

struct My_tcpdata *create_data(char cmd, char *data, int data_len)
{
    struct My_tcpdata *tcpdata = (struct My_tcpdata*)malloc(sizeof(struct My_tcpdata));
    tcpdata->cmd = cmd;
    tcpdata->data = data;
    tcpdata->data_len = data_len;
    return tcpdata;
}

void my_tcp_send(struct My_tcpdata *tcpdata)
{
    char sendbuff[11];
    sprintf(sendbuff, "%c%08d", tcpdata->cmd, tcpdata->data_len);
    Serial1.write(sendbuff);
    Serial1.write(tcpdata->data);
    Serial0.printf("发送成功:cmd:%c,data_len=%d ", tcpdata->cmd, tcpdata->data_len);
    free(tcpdata);
}
void send_string(char cmd, char *str)
{
    my_tcp_send(create_data(cmd, str, strlen(str)));
}
void send_hex(char cmd, char *buff, int len)
{
    my_tcp_send(create_data(cmd, buff, len));
}
