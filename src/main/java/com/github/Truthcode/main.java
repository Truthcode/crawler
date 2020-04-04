package com.github.Truthcode;

public class main {
    public static void main(String[] args) {
        CrawlerDao dao = new MyBatisCrawlerDao();
        for (int i = 0; i <8; i++) {
            new Crawler(dao).start();
        }
    }
}
