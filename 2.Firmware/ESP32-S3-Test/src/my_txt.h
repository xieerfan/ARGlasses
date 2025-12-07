#ifndef __my_txt_h
#define __my_txt_h 


#include "Arduino.h"

int json2txt(const char* path,const char* outpath);
void suoyin_creat(const char* file_path,const char* outfile_path);
void get_txt(const char* txtpath, const char* syfilepath ,int Y);


void json_flush();//刷新目录
void delete_json_file();//删除全部json文件
void display_json(const char* jsonname,int y);
void display_txt(const char* txtname,int y);

#endif