package com.github.Truthcode;

import org.apache.http.HttpEntity;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class Main {
    public static void main(String[] args) throws IOException {
        //未处理的数据池
        List<String> linkPool = new ArrayList();
        //已处理的数据池
        Set<String> processedLinks = new HashSet<>();
        linkPool.add("https://sina.cn/");

        while (true) {
            //若连接池为空就退出
            if (linkPool.isEmpty()) {
                break;
            }
            String link = linkPool.remove(linkPool.size() - 1);
            //若连接已经被处理即跳过
            if (processedLinks.contains(link)) {
                continue;
            }
            //若连接是新浪有关的新闻页面就进入
            if (isInterstingLink(link)) {
                Document doc = httpGetAndParseHtml(link);
                Elements links = doc.select("a");
                for (Element aTag : links) {
                    linkPool.add(aTag.attr("href"));
                }

                //假如这个是新闻页面详情页面，就存入数据库
                storeIntoDatabaseIfItIsNewsPage(doc);

                processedLinks.add(link);
            }
        }
    }

    private static void storeIntoDatabaseIfItIsNewsPage(Document doc) {
        Elements articleTags = doc.select("article");
        if (!articleTags.isEmpty()) {
            for (Element articleTag : articleTags) {
                String title = articleTags.get(0).child(0).text();
                System.out.println(title);
            }
        }
    }


    private static Document httpGetAndParseHtml(String link) throws IOException {
        CloseableHttpClient httpclient = HttpClients.createDefault();

        System.out.println(link);
        if ((link.startsWith("//"))) {
            link = "https" + link;
        }

        HttpGet httpGet = new HttpGet(link);
        httpGet.addHeader("user-agent", "Mozilla/5.0 (Windows NT 10.0; WOW64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/80.0.3987.116 Safari/537.36)");


        try (CloseableHttpResponse response1 = httpclient.execute(httpGet)) {
            System.out.println(response1.getStatusLine());
            HttpEntity entity1 = response1.getEntity();
            String html = EntityUtils.toString(entity1);

            return Jsoup.parse(html);

        }
    }

    private static boolean isInterstingLink(String link) {
        return (isIndexPage(link) || isNewsPage(link)) && isNotLoginPage(link);
    }

    private static boolean isIndexPage(String link) {
        return "https://sina.cn/".equals(link);
    }

    private static boolean isNewsPage(String link) {
        return link.contains("news.sina.cn");
    }

    private static boolean isNotLoginPage(String link) {
        return !link.contains("passport.sina.cn");
    }
}
