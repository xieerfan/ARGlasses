#include "my_txt.h"
#include "json_parser.h"
#include <dirent.h>   // 为了 opendir/readdir
#include "SD_MMC.h"
int json2txt(const char* path,const char* outpath) {//解析JSON
    FILE* file = fopen(path, "r");
    if (file == NULL) {
        Serial0.printf("Failed to open file for reading: %s\n", path);
        return 0;
    }
    // 获取文件大小
    fseek(file, 0, SEEK_END);
    long file_size = ftell(file);
    fseek(file, 0, SEEK_SET);

    // 分配内存
    char* buffer = (char*)malloc(file_size + 1);
    if (buffer == NULL) {
        Serial0.printf( "Failed to allocate memory for file content\n");
        fclose(file);
        return 0;
    }

    // 读取文件内容
    size_t read_size = fread(buffer, 1, file_size, file);
    if (read_size != file_size) {
        Serial0.printf( "Failed to read file content\n");
        free(buffer);
        fclose(file);
        return 0;
    }
    // 添加字符串结束符
    buffer[file_size] = '\0';
    fclose(file);
    // 初始化 JSON 解析上下文
    jparse_ctx_t jctx;
    int ret = json_parse_start(&jctx, buffer, strlen(buffer));
    if (ret == OS_SUCCESS) {
        // 分配内存用于存储解析结果
        char* str_val = (char*)malloc(file_size + 1);
        if (str_val == NULL) {
            Serial0.printf( "Failed to allocate memory for JSON result\n");
            free(buffer);
            return 0;
        }

        // 获取 analysis_result 字段
        if (json_obj_get_string(&jctx, "analysis_result", str_val, file_size + 1) == OS_SUCCESS) {
            ESP_LOGI(TAG, "JSON Parser: %s", str_val);

            // 将解析结果写入新文件
            FILE* f = fopen(outpath, "w");
            if (f == NULL) {
                Serial0.printf( "Failed to open file for writing\n");
                return 0;
            } else {
                fprintf(f, "%s", str_val); // 写入内容
                fclose(f);
                
            }
        } else {
            Serial0.printf( "Failed to get 'analysis_result'\n");
            return 0;
        }
        free(str_val);
        json_parse_end(&jctx);
    } else {
        Serial0.printf( "Failed to parse JSON\n");
        return 0;
    }
    free(buffer);
    return 1;
}


#define TXT_LINES 6
#define TXT_LINE_LENGTH 45

static char txt[TXT_LINES][TXT_LINE_LENGTH + 1];

// 索引文件生成函数竖版
void suoyin_creat(const char* file_path,const char* outfile_path) {
    FILE* file = fopen(file_path, "rb");
    if (!file) {
        fprintf(stderr, "Failed to open %s\n", file_path);
        return;
    }

    unsigned long l = 0, d = 0, y = 0;
    long s = 0;
    int v = -1;

    // 获取文件大小
    fseek(file, 0, SEEK_END);
    s = ftell(file);
    fseek(file, 0, SEEK_SET);

    // 删除旧的索引文件并创建新的索引文件
    remove(outfile_path);
    FILE* SYfile = fopen(outfile_path, "w");
    if (!SYfile) {
        fprintf(stderr, "Failed to open txtSY.txt for writing\n");
        fclose(file);
        return;
    }

    while (1) {
        int t = fgetc(file);
        if (t == EOF) {
            break;
        }

        if ((t >= 0xB0 && t <= 0xF7) || t == 0xE3) {  // 中文字符 UTF-8一个中文字符占3个字节
            fgetc(file);  // 读取第二个字节
            fgetc(file);  // 读取第三个字节
            d += 3;
        } else if (t == '\n') {  // 遇到换行符直接换行
            l++;
            d = 0;
        } else {  // 英文字符 一个字符占用1个字节
            d++;
        }

        if (d > TXT_LINE_LENGTH) {  // 一行满60个字节换行
            d = 0;
            l++;
        }

        if (l >= TXT_LINES) {  // 一页显示24行 停止获取
            long t = ftell(file);
            int value = (int)((float)y / (float)(s / TXT_LINES) * 100);
            if (v != value) {
                v = value;
                Serial0.printf("处理进度：%d %%\n", v);
            }

            fprintf(SYfile, "L%ld:%ld\n", y + 1, t);
            l = 0;
            y++;
        }
    }

    fclose(SYfile);
    fclose(file);
    Serial0.printf("索引文件生成完成\n");
}

// 获取txt显示缓存 
void get_txt(const char* txtpath, const char* syfilepath ,int Y) {
    FILE* file = fopen(txtpath, "r");
    if (!file) {
        Serial0.printf( "Failed to open example.txt\n");
        return;
    }
    int i = 0, l = 0, d = 0;
    int address = 0;
    char ST_L[60]; // 假设每行不超过100个字符
    int ST_L_len = 0;
    if (Y != 0) {
        FILE* SYfile = fopen(syfilepath, "r");
        if (!SYfile) {
            fprintf(stderr, "Failed to open txtSY\n");
            return;
        }

        while (fgets(ST_L, sizeof(ST_L), SYfile)) {
            ST_L_len = strlen(ST_L);
            if (ST_L_len > 0 && ST_L[ST_L_len - 1] == '\n') {
                ST_L[ST_L_len - 1] = '\0';
            }

            char* colon = strchr(ST_L, ':');
            if (colon) {
                *colon = '\0';
                int line = atoi(ST_L + 1); // "L" 后面的数字
                if (line == Y) {
                    address = atoi(colon + 1);
                    break;
                }
            }
        }
        fclose(SYfile);
    }
    fseek(file, address, SEEK_SET);
    for (int i = 0; i < TXT_LINES; i++) {
        txt[i][0] = '\0';
    }
    while (1) {
        int t = fgetc(file);
        if (t == EOF) {
            break;
        }
        if ((t >= 0xB0 && t <= 0xF7) || t == 0xE3) {  // 中文字符 UTF-8一个中文字符占3个字节
            if (d + 3 > TXT_LINE_LENGTH) {
                d = 0;
                l++;
                if (l >= TXT_LINES) {
                    break;
                }
            }
            txt[l][d++] = (char)t;
            txt[l][d++] = (char)fgetc(file);
            txt[l][d++] = (char)fgetc(file);
        } else if (t == '\n') {                       // 遇到换行符直接换行
            txt[l][d]='\0';
            d = 0;
            l++;
            if (l >= TXT_LINES) {
                break;
            }
        } else {                                      // 英文字符 一个字符占用1个字节
            if (d >= TXT_LINE_LENGTH) {
                d = 0;
                l++;
                if (l >= TXT_LINES) {
                    break;
                }
            }
            txt[l][d++] = (char)t;
        }
        if (d >= TXT_LINE_LENGTH) {                   // 一行满60个字节换行
            d = 0;
            l++;
            if (l >= TXT_LINES) {
                break;
            }
        }
    }
    fclose(file);
}



void json_flush(){
    File root = SD_MMC.open("/json");
    while (File f = root.openNextFile()) {
        uint16_t num;
        if (sscanf(f.name(), "json_%hu.json", &num) == 1){
            Serial0.println(f.name());
            char jsonPath[256];
            char syPath[256];
            char txtPath[256];
            snprintf(jsonPath, sizeof(jsonPath), "/sdcard/json/%s", f.name());
            snprintf(syPath, sizeof(syPath), "/sdcard/json/sy_%s", f.name());
            snprintf(txtPath, sizeof(txtPath), "/sdcard/json/txt_%s", f.name());
            if(json2txt(jsonPath,txtPath)){
                suoyin_creat(txtPath,syPath);
                display_txt(f.name(),1);
            }

        }
        f.close();   
    }
}

void delete_json_file() { 
    File root = SD_MMC.open("/json");
    while (File f = root.openNextFile()) {
        f.close(); 
        SD_MMC.remove(f.path());
    }
}


void display_json(const char* jsonname,int y) { 
    char sypath[256];
    char txtpath[256];
    sprintf(sypath, "/sdcard/json/sy_%s", jsonname);
    sprintf(txtpath, "/sdcard/json/txt_%s", jsonname);
    get_txt(txtpath, sypath, y);
    for (int i = 0; i < TXT_LINES; i++) {
        Serial0.printf("%s\n", txt[i]);
    }
}
void display_txt(const char* txtname,int y) { 
    char sypath[256];
    char txtpath[256];
    sprintf(sypath, "/sdcard/txt/sy_%s", txtname);
    sprintf(txtpath, "/sdcard/txt/txt_%s", txtname);
    get_txt(txtpath, sypath, y);
    for (int i = 0; i < TXT_LINES; i++) {
        Serial0.printf("%s\n", txt[i]);
    }
}
