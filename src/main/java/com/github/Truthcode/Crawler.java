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
import java.sql.SQLException;
import java.util.stream.Collectors;

public class Crawler extends Thread {
    private CrawlerDao dao;
    public Crawler(CrawlerDao dao) {
        this.dao = dao;
    }

    public void run() {
        String link;
        //从数据库中加载下一个连接，能够加载就进行循环
        try {
            while ((link = dao.getNextLinkThenDelete()) != null) {
                //询问数据库，当前链接是否已经被处理过？
                //若连接已经被处理即跳过
                if (dao.isLinkProcessed(link)) {
                    continue;
                }

                //若连接是news有关的新闻页面就进入
                if (isInterstingLink(link)) {
                    Document doc = httpGetAndParseHtml(link);

                    parseUrlsFromPageAndStoreIntoDatabase(doc);

                    storeIntoDatabaseIfItIsNewsPage(doc, link);

                    dao.insertProcessedLind(link);
                    //dao.updateDatabase(link, "INSERT into LINKS_ALREADY_PROCESSED (LINK) values (?)");
                }
            }
        } catch (SQLException | IOException e) {
            throw new RuntimeException(e);
        }
    }

    private void parseUrlsFromPageAndStoreIntoDatabase(Document doc) throws SQLException {
        //假如这个是新闻页面详情页面，就存入数据库
        for (Element aTag : doc.select("a")) {
            String href = aTag.attr("href");
            if (href.toLowerCase().startsWith("javascript") || href.equals("")) {
                continue;
            }
            dao.insertLinkToBeProcessed(href);
        }
    }


    private void storeIntoDatabaseIfItIsNewsPage(Document doc, String link) throws SQLException {
        Elements articleTags = doc.select("article");
        if (!articleTags.isEmpty()) {
            for (Element articleTag : articleTags) {
                String title = articleTags.get(0).child(0).text();
                //Elements paragraphs = articleTag.select("p");
                String content = articleTag.select("p").stream().map(Element::text).collect(Collectors.joining("\n"));
                dao.inserNewsIntoDatabase(link, title, content);

                System.out.println(title);
            }
        }
    }

    private Document httpGetAndParseHtml(String link) throws IOException {
        CloseableHttpClient httpclient = HttpClients.createDefault();

        System.out.println(link);
        if ((link.startsWith("//"))) {
            link = "https" + link;
        }

        HttpGet httpGet = new HttpGet(link);
        httpGet.addHeader("user-agent", "Mozilla/5.0 (Windows NT 10.0; WOW64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/80.0.3987.116 Safari/537.36)");

        try (CloseableHttpResponse response1 = httpclient.execute(httpGet)) {
            //System.out.println(response1.getStatusLine());
            HttpEntity entity1 = response1.getEntity();
            String html = EntityUtils.toString(entity1);
            return Jsoup.parse(html);

        }
    }

    private boolean isInterstingLink(String link) {
        return (isIndexPage(link) || isNewsPage(link)) && isNotLoginPage(link);
    }

    private boolean isIndexPage(String link) {
        return "https://sina.cn/".equals(link);
    }

    private boolean isNewsPage(String link) {
        return link.contains("news.sina.cn");
    }

    private boolean isNotLoginPage(String link) {
        return !link.contains("passport.sina.cn") && !link.contains("https:\\/\\/news.sina.cn\\/news_zt\\/keyword.d.html?k=");
    }
}
