#include "Arduino.h"
#include <jbd013_api.h>
#include <SPI.h>
#include <driver/spi_master.h>
#include "lvgl.h"
// #include "font_alipuhui20.h"


#define PANEL_WIDTH  480
#define PANEL_HEIGHT 480

static lv_disp_draw_buf_t draw_buf;
lv_obj_t *label;

// 初始化SPI和显示屏
void initDisplay() {
    // 配置SPI
    SPI.begin(SPI_CLK, -1, SPI_MOSI, SPI_CS);
    // SPI.setBitOrder(MSBFIRST);
    // SPI.setDataMode(SPI_MODE0);
    // SPI.setClockDivider(SPI_CLOCK_DIV4);
    SPI.beginTransaction(SPISettings(8*1000000, MSBFIRST, SPI_MODE0)); 
    pinMode(SPI_CS, OUTPUT);
    digitalWrite(SPI_CS, HIGH);
    delay(10);
    // 初始化面板
    panel_init();
    
}

void oled_clr_cache(){
    clr_cache();  // 清除显示缓存
    send_cmd(SPI_SYNC);
    delay(100);
}


// void my_disp_flush(lv_disp_drv_t *disp_drv, const lv_area_t *area, lv_color_t *color_p)
// {
//     static u8 line[PANEL_WIDTH/2];
//     for (int y = area->y1; y <= area->y2; y++) {
//         int x1 = area->x1;
//         int w  = area->x2 - x1 + 1;
//         /* 构造当前行像素 */
//         for (int i = x1; i < x1+w; i++) {
//             if(i%2==0){
//                 line[i/2] = (color_p->full ?(0x0F&line[i/2]) : (0xF0|line[i/2]));
//             }else{
//                 line[i/2] = (color_p->full ?(0xF0&line[i/2]) : (0x0F|line[i/2]));
//             }
//             color_p++;
//         }
//         /* 从正确列开始写这一行 */
//         send_line(0,y,line, w/2);
//     }
//     lv_disp_flush_ready(disp_drv);
// }

static u8 g_fb[PANEL_WIDTH * PANEL_HEIGHT / 2];   // 全局帧缓冲
void my_disp_flush(lv_disp_drv_t *disp_drv, const lv_area_t *area, lv_color_t *color_p)
{
    for (int y = area->y1; y <= area->y2; y++) {
        for (int x = area->x1; x <= area->x2; x++) {        
            u32 byte_idx = (y * PANEL_WIDTH + x) >> 1;     // 8 像素/字节
            if (x % 2) {                                   // 低 4 位
                g_fb[byte_idx] = (color_p->full ?(0x0F&g_fb[byte_idx]) : (0xF0|g_fb[byte_idx]));
            } else {                                       // 高 4 位
                g_fb[byte_idx] = (color_p->full ?(0xF0&g_fb[byte_idx]) : (0x0F|g_fb[byte_idx]));
            }
            color_p++;
        }
        spi_wr_cache(0, y, g_fb+y*PANEL_WIDTH/2, PANEL_WIDTH/2);
    }

    /* ③ 整屏刷新 */
    /* ④ 告诉 LVGL 刷完了 */
    send_cmd(SPI_SYNC);
    delay_ms(1);                   // 芯片要求的 1 ms
    lv_disp_flush_ready(disp_drv);
}


static lv_color_t buf_1[PANEL_WIDTH * 20];
static lv_color_t buf_2[PANEL_WIDTH * 20];
// LV_FONT_DECLARE(font_alipuhui20)
void bsp_lvgl_init(void){
    
    initDisplay();
    lv_init();
    lv_disp_draw_buf_init(&draw_buf, buf_1, buf_2,PANEL_WIDTH * 20);
    /* Create a timer and set its callback */
    /*Initialize the display*/
    static lv_disp_drv_t disp_drv;
    lv_disp_drv_init(&disp_drv);
    disp_drv.hor_res = PANEL_WIDTH;
    disp_drv.ver_res = PANEL_HEIGHT;
    disp_drv.flush_cb = my_disp_flush;
    disp_drv.draw_buf = &draw_buf;
    disp_drv.full_refresh = 0;
    disp_drv.sw_rotate = 1;           // 启用软件旋转
    disp_drv.rotated = LV_DISP_ROT_270; // 
    lv_disp_drv_register(&disp_drv);

}