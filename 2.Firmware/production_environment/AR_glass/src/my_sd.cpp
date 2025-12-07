#include "my_sd.h"
#include "Arduino.h"
#include "esp_vfs_fat.h"
#include "sdmmc_cmd.h"
#include "SD_MMC.h"
#include "esp_log.h"
#include <dirent.h>   // 为了 opendir/readdir



#define TAG  "sdcard"

#define MOUNT_POINT              "/sdcard"
#define EXAMPLE_MAX_CHAR_SIZE    64


// 列出挂载点根目录
void sd_list_root(void)
{
    const char *root = MOUNT_POINT"/TXT";          // "/sdcard"
    DIR *dir = opendir(root);
    if (!dir) {
        ESP_LOGE(TAG, "无法打开根目录 %s", root);
        return;
    }

    struct dirent *entry;
    Serial.printf("\r\n--- SD 根目录文件列表 ---\r\n");
    while ((entry = readdir(dir)) != nullptr) {
        /* 简单过滤隐藏文件 */
        if (entry->d_name[0] == '.') continue;

        /* 拼接完整路径，顺便区分文件/文件夹 */
        char full_path[256];
        snprintf(full_path, sizeof(full_path), "%s/%s", root, entry->d_name);

        struct stat st;
        if (stat(full_path, &st) == 0) {
            const char *type = (st.st_mode & S_IFDIR) ? "DIR " : "FILE";
            Serial.printf("%s  %s  %lu bytes\r\n", type, entry->d_name,
                          (unsigned long)st.st_size);
        } else {
            Serial.printf("????  %s\r\n", entry->d_name);
        }
    }
    closedir(dir);
}

static uint16_t file_seq = 0;

static uint16_t get_picture_max_seq(void)
{
    const char *folder = MOUNT_POINT "/picture";
    DIR *dir = opendir(folder);
    uint16_t max_n = 0;

    if (!dir) {          // 目录不存在就返回 0
        return 0;
    }

    struct dirent *entry;
    while ((entry = readdir(dir)) != nullptr) {
        /* 只处理形如 "IMG_xxxx.jpg" 的文件 */
        uint16_t num;
        if (sscanf(entry->d_name, "IMG_%hu.jpg", &num) == 1) {
            if (num > max_n) max_n = num;
        }
    }
    closedir(dir);
    return max_n;
}

int save_jpg_file(const uint8_t *jpeg_buf, size_t jpeg_size)
{
    if (!jpeg_buf || jpeg_size == 0) {
        ESP_LOGE(TAG, "Empty buffer");
        return 2;
    }

    /* 生成文件名：/sdcard/IMG_0001.jpg */
    char path[64];
    snprintf(path, sizeof(path), MOUNT_POINT "/picture/IMG_%04u.jpg", file_seq++);
    ESP_LOGI(TAG, "Saving → %s , %zu B", path, jpeg_size);

    FILE *f = fopen(path, "wb");          // 二进制模式！
    if (!f) {
        ESP_LOGE(TAG, "fopen failed");
        return 1;
    }

    size_t w = fwrite(jpeg_buf, 1, jpeg_size, f);
    fclose(f);

    if (w != jpeg_size) {
        ESP_LOGE(TAG, "fwrite short %zu/%zu", w, jpeg_size);
        return 2;
    }

    ESP_LOGI(TAG, "Save OK");
    return 0;
}


static FILE *my_fp = nullptr;   // 文件句柄

void start_write(const char *path)
{
    if (my_fp) {           // 如果之前没关闭，先关闭
        fclose(my_fp);
        my_fp = nullptr;
    }
    my_fp = fopen(path, "wb");
    if (!my_fp) {
        Serial.printf( "start_write: fopen %s failed", path);
    }
}

void update_write(uint8_t *data, size_t len)
{
    if (!my_fp) {
        Serial.printf("update_write: file not opened\n");
        return;
    }
    size_t w = fwrite(data, 1, len, my_fp);   // 顺序正确
    if (w != len) {
        Serial.printf("update_write: fwrite short %zu/%zu", w, len);
    }
}
void end_write()
{
    if (!my_fp) {
        Serial.printf("end_write: file not opened\n");
        return;
    }
    fclose(my_fp);
    my_fp = nullptr;
}

void my_sd_init() {
    SD_MMC.setPins(BSP_SD_CLK,BSP_SD_CMD,BSP_SD_D0);
    SD_MMC.begin("/sdcard", true);
    file_seq = get_picture_max_seq() + 1;
    Serial.printf("Start seq from %u\n", file_seq);
}

void delete_file(const char *path){
    if (SD_MMC.exists(path)){
        SD_MMC.remove(path);
    }
}
