#ifndef __my_txt_h
#define __my_txt_h 


#include "Arduino.h"

#define TXT_LINES 6
#define TXT_LINE_LENGTH 60


int json2txt(const char* path,const char* outpath);
void suoyin_creat(const char* file_path,const char* outfile_path);
void get_txt(const char* txtpath, const char* syfilepath ,int Y);


void delete_json_file();//删除全部json文件
void display_json(const char* jsonname,int y,int* symax);
void display_txt(const char* txtname,int y,int* symax);
int get_total_pages(const char* syfilepath);
#endif