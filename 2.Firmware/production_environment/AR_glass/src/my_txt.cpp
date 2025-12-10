#include "my_txt.h"
#include "json_parser.h"
#include <dirent.h>   // 为了 opendir/readdir
#include "SD_MMC.h"
#include "my_uart.h"

static File gDir;                // 目录句柄
static bool gInited = false;     // 句柄是否已打开

int json2txt(const char* path,const char* outpath) {//解析JSON
    char path0[256];
    sprintf(path0, "/sdcard/%s",path);
    FILE* file = fopen(path0, "r");
    if (file == NULL) {
        Serial.printf("Failed to open file for reading: %s\n", path0);
        return 0;
    }
    // 获取文件大小
    fseek(file, 0, SEEK_END);
    long file_size = ftell(file);
    fseek(file, 0, SEEK_SET);

    // 分配内存
    char* buffer = (char*)malloc(file_size + 1);
    if (buffer == NULL) {
        Serial.printf( "Failed to allocate memory for file content\n");
        fclose(file);
        return 0;
    }

    // 读取文件内容
    size_t read_size = fread(buffer, 1, file_size, file);
    if (read_size != file_size) {
        Serial.printf( "Failed to read file content\n");
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
            Serial.printf( "Failed to allocate memory for JSON result\n");
            free(buffer);
            return 0;
        }

        // 获取 analysis_result 字段
        if (json_obj_get_string(&jctx, "analysis_result", str_val, file_size + 1) == OS_SUCCESS) {
            ESP_LOGI(TAG, "JSON Parser: %s", str_val);

            sprintf(path0, "/sdcard/%s",outpath);
            // 将解析结果写入新文件
            FILE* f = fopen(path0, "w");
            if (f == NULL) {
                Serial.printf( "Failed to open file for writing\n");
                return 0;
            } else {
                fprintf(f, "%s", str_val); // 写入内容
                fclose(f);
                
            }
        } else {
            Serial.printf( "Failed to get 'analysis_result'\n");
            return 0;
        }
        free(str_val);
        json_parse_end(&jctx);
    } else {
        Serial.printf( "Failed to parse JSON\n");
        return 0;
    }
    free(buffer);
    return 1;
}



static char txt[TXT_LINES][TXT_LINE_LENGTH + 1];

// 索引文件生成函数竖版
void suoyin_creat(const char* file_path,const char* outfile_path) {
    char path0[256];
    sprintf(path0, "/sdcard/%s",file_path);
    FILE* file = fopen(path0, "rb");
    if (!file) {
        fprintf(stderr, "Failed to open %s\n", path0);
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
    sprintf(path0, "/sdcard/%s",outfile_path);
    remove(path0);
    FILE* SYfile = fopen(path0, "w");
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
                Serial.printf("处理进度：%d %%\n", v);
            }

            fprintf(SYfile, "L%ld:%ld\n", y + 1, t);
            l = 0;
            y++;
        }
    }

    fclose(SYfile);
    fclose(file);
    Serial.printf("索引文件生成完成\n");
}

// 获取txt显示缓存 
void get_txt(const char* txtpath, const char* syfilepath ,int Y) {
    char path0[256];
    sprintf(path0, "/sdcard/%s",txtpath);
    FILE* file = fopen(path0, "r");
    if (!file) {
        Serial.printf( "Failed to open %s\n",path0);
        return;
    }
    int i = 0, l = 0, d = 0;
    int address = 0;
    char ST_L[60]; // 假设每行不超过100个字符
    int ST_L_len = 0;
    if (Y != 0) {
        sprintf(path0, "/sdcard/%s",syfilepath);
        FILE* SYfile = fopen(path0, "r");
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



void delete_json_file() { 
    File root = SD_MMC.open("/json");
    while (File f = root.openNextFile()) {
        f.close(); 
        SD_MMC.remove(f.path());
    }
}


void display_json(const char* jsonname,int y,int* symax) { 
    if(gInited){
        gDir.close();
        gInited = false;   
    }
    char sypath[256];
    char txtpath[256];
    sprintf(sypath, "%s.sy", jsonname);
    sprintf(txtpath, "%s.txt", jsonname);
    if(!SD_MMC.exists(txtpath)){
        if(json2txt(jsonname,txtpath)){
        suoyin_creat(txtpath,sypath);
        }else{
            Serial.printf("json转换txt失败\n");
            return;
        }
    }
    *symax=get_total_pages(sypath);
    get_txt(txtpath, sypath, y);
    for (int i = 0; i < TXT_LINES; i++) {
        Serial.printf("%s\n", txt[i]);
    }
    char str[TXT_LINES*(TXT_LINE_LENGTH + 1)];
    sprintf(str,"%s\n%s\n%s\n%s\n%s\n%s",txt[0],txt[1],txt[2],txt[3],txt[4],txt[5]);
    send_content(str);
}
void display_txt(const char* txtname,int y,int* symax) { 
    Serial.println(txtname);
    if(!SD_MMC.exists(txtname)){
        Serial.printf("文件不存在\n");
        return;
    }
    char sypath[256];
    char txtpath[256];
    sprintf(sypath, "%s.sy", txtname);
    sprintf(txtpath, "%s", txtname);
    if(SD_MMC.exists(sypath)){
        Serial.printf("索引文件存在\n");
    }else{ 
        Serial.printf("创建索引文件\n");
        suoyin_creat(txtpath, sypath);
    }
    *symax=get_total_pages(sypath);
    get_txt(txtpath, sypath, y);
    for (int i = 0; i < TXT_LINES; i++) {
        Serial.printf("%s\n", txt[i]);
    }
    char str[TXT_LINES*(TXT_LINE_LENGTH + 1)];
    sprintf(str,"%s\n%s\n%s\n%s\n%s\n%s",txt[0],txt[1],txt[2],txt[3],txt[4],txt[5]);
    send_content(str);
}


int get_total_pages(const char* syfilepath)
{
    char path[256];
    snprintf(path, sizeof(path), "/sdcard/%s", syfilepath); // 按你路径规则来
    FILE* f = fopen(path, "r");
    if (!f) {
        Serial.printf("sy file missing: %s\n", path);
        return 0;
    }

    int max_page = 0;
    char line[256];
    while (fgets(line, sizeof(line), f)) {
        line[strcspn(line, "\r\n")] = 0;          // 去换行
        char* colon = strchr(line, ':');
        if (!colon || line[0] != 'L') continue;   // 格式不对跳过

        int page = atoi(line + 1);                // 取 L123 里的 123
        if (page > max_page) max_page = page;     // 更新最大值
    }
    fclose(f);
    return max_page;   // 就是总页数
}



/* 每调一次返回下一个路径；扫完返回 "" */
String getNextFilePath() {
  if (!gInited) {                // 第一次调用
    gDir = SD_MMC.open("/json/");
    gInited = true;
  }

  File entry = gDir.openNextFile();
  if (!entry) {                  // 目录遍历完
    gDir.close();
    gInited = false;
    return "";
  }

  String path = entry.path();    // 直接拿绝对路径
  entry.close();                 // 只关文件，不关目录
  return path;
}