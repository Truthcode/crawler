package com.github.Truthcode;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
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
import java.sql.*;
import java.util.stream.Collectors;

public class Main {
    private static final String USER_NAME = "root";
    private static final String PASSWORD = "root";

    private static String getNextLink(Connection connection, String sql) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(sql); ResultSet resultSet = statement.executeQuery()) {
            while (resultSet.next()) {
                return resultSet.getString(1);
            }
        }
        return null;
    }

    private static String getNextLinkThenDelete(Connection connection) throws SQLException {
        String link = getNextLink(connection, "select link from LINKS_TO_BE_PROCESSED");
        if (link != null) {
            updateDatabase(connection, link, "DELETE FROM LINKS_TO_BE_PROCESSED where link = ?");
        }
        return link;
    }

    @SuppressFBWarnings("DMI_CONSTANT_DB_PASSWORD")
    public static void main(String[] args) throws IOException, SQLException {
        Connection connection = DriverManager.getConnection("jdbc:h2:file:D:/study/workplace/crawler/news", USER_NAME, PASSWORD);

        String link;
        //从数据库中加载下一个连接，能够加载就进行循环
            while ((link = getNextLinkThenDelete(connection)) != null) {
                //询问数据库，当前链接是否已经被处理过？
                //若连接已经被处理即跳过
                if (isLinkProcessed(connection, link)) {
                    continue;
                }

                //若连接是news有关的新闻页面就进入
                if (isInterstingLink(link)) {
                    Document doc = httpGetAndParseHtml(link);

                    parseUrlsFromPageAndStoreIntoDatabase(connection, doc);

                    storeIntoDatabaseIfItIsNewsPage(connection,doc,link);

                    updateDatabase(connection, link, "INSERT into LINKS_ALREADY_PROCESSED (LINK) values (?)");
                }
            }
        }

    private static void parseUrlsFromPageAndStoreIntoDatabase(Connection connection, Document doc) throws SQLException {
        //假如这个是新闻页面详情页面，就存入数据库
        for (Element aTag : doc.select("a")) {
            String href = aTag.attr("href");
            if (href.toLowerCase().startsWith("javascript") || href.equals("")){
                continue;
            }
            updateDatabase(connection, href, "INSERT into LINKS_TO_BE_PROCESSED (LINK) values (?)");
        }
    }

    private static boolean isLinkProcessed(Connection connection, String link) throws SQLException {
        ResultSet resultSet = null;
        try (PreparedStatement statement = connection.prepareStatement("SELECT LINK FROM LINKS_ALREADY_PROCESSED WHERE LINK = ?")) {
            statement.setString(1, link);
            resultSet = statement.executeQuery();
            while (resultSet.next()) {
                return true;
            }
        } finally {
            if (resultSet != null) {
                resultSet.close();
            }
        }
        return false;
    }

    private static void updateDatabase(Connection connection, String link, String sql) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, link);
            statement.executeUpdate();
        }
    }

    private static void storeIntoDatabaseIfItIsNewsPage(Connection connection,Document doc,String link) throws SQLException {
        Elements articleTags = doc.select("article");
        if (!articleTags.isEmpty()) {
            for (Element articleTag : articleTags) {
                String title = articleTags.get(0).child(0).text();
                //Elements paragraphs = articleTag.select("p");
                String content = articleTag.select("p").stream().map(Element::text).collect(Collectors.joining("\n"));

                try(PreparedStatement statement = connection.prepareStatement(("insert into news (url ,title,content,CREATED_AT,modified_at)values(?,?,?,now(),now())" ))){
                    statement.setString(1,link);
                    statement.setString(2,title);
                    statement.setString(3,content);
                    statement.executeUpdate();
                }

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
            //System.out.println(response1.getStatusLine());
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
        return !link.contains("passport.sina.cn") && !link.contains("https:\\/\\/news.sina.cn\\/news_zt\\/keyword.d.html?k=");
    }
}
