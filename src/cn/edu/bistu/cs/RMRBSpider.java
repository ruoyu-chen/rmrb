package cn.edu.bistu.cs;

import org.apache.log4j.Logger;
import us.codecraft.webmagic.Page;
import us.codecraft.webmagic.Site;
import us.codecraft.webmagic.Spider;
import us.codecraft.webmagic.pipeline.FilePipeline;
import us.codecraft.webmagic.pipeline.JsonFilePipeline;
import us.codecraft.webmagic.processor.PageProcessor;

import java.util.ArrayList;
import java.util.List;

public class RMRBSpider implements PageProcessor{

    private Site site = Site.me().setCycleRetryTimes(20).setSleepTime(2000).setTimeOut(20000);

    private static final Logger log = Logger.getLogger(RMRBSpider.class);

    //http://www.ziliaoku.org/rmrb
    /**
     * 月份目录页的URL正则表达式
     */
    private static final String month_page_regex = "http://www.ziliaoku.org/rmrb/[0-9]{4}-[0-9]{2}";

    /**
     * 日期页的URL正则表达式
     */
    private static final String day_page_regex = "http://www.ziliaoku.org/rmrb/[0-9]{4}-[0-9]{2}-[0-9]{2}";

    /**
     * 版面页的URL正则表达式
     */
    private static final String article_page_regex = "http://www.ziliaoku.org/rmrb/[0-9]{4}-[0-9]{2}-[0-9]{2}-[0-9]{1}\\#[0-9]+";

    private static String startPage = "http://www.ziliaoku.org/rmrb";

    private static String ua = "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_13) AppleWebKit/604.1.38 (KHTML, like Gecko) Version/11.0 Safari/604.1.38";

    public static void main(String[] args){
        //首页地址：http://www.ziliaoku.org/rmrb
        Spider spider = Spider
                .create(new RMRBSpider())
                .addPipeline(new JsonFilePipeline("./rmrb"))
                // 设置起始URL
                .addUrl(startPage);
        spider.run();
    }

    @Override
    public void process(Page page) {
        String url = page.getRequest().getUrl();
        if(url.equals(startPage)){
            //首页 http://www.ziliaoku.org/rmrb
            //月份目录页 http://www.ziliaoku.org/rmrb/1946-05
            //某一天的报纸内容页 http://www.ziliaoku.org/rmrb/1946-05-15
            List<String> pages = page.getHtml().xpath("//div[@id='box']//a/@href").all();
            page.addTargetRequests(pages);//将各月的目录页提取出来放到待抓取队列里
            //page.addTargetRequest(pages.get(0));
            page.setSkip(true);//在PipeLine阶段跳过处理这个页面
        }else if(url.matches(month_page_regex)){
            //某个月的目录页
            List<String> pages = page.getHtml().xpath("//div[@id='box']/div[@id='month_box']//a/@href").all();
            page.addTargetRequests(pages);//将这个月内每一天的内容页面提取出来放到待抓取队列里
            //page.addTargetRequest(pages.get(0));
            log.info("处理月份目录页:"+url);
            page.setSkip(true);//在PipeLine阶段跳过处理这个页面
        }else if(url.matches(day_page_regex)){
            //某一天的内容页
            log.info("处理日期内容页"+page.getRequest().getUrl());
            /**
             * 每一天的页面，分为多个版面，每个版面上的所有文章共用一个页面。
             * 但是同一版面内的每一篇文章在这一天的内容页里都包含一条链接，
             * 因此，多次抓取同一个版面上的文章是没有必要的，只抓取该版面上的第一篇文章的地址就可以了
             */
            List<String> pages = page.getHtml().xpath("//div[@id='box']/div[@class='main']/div[@class='box']/ul/li[1]/a/@href").all();
            page.addTargetRequests(pages);//将各版面的第一篇文章添加到待抓取队列中
            page.setSkip(true);//在PipeLine阶段跳过处理这个页面
        } else if(url.matches(article_page_regex)){
            //某一天的某一个版面的内容页
            log.info("处理版面内容页"+url);
            //http://www.ziliaoku.org/rmrb/1946-05-15-4#641
            List<Article> articles = new ArrayList<>();
            String ymd = url.substring(url.lastIndexOf('/')+1,url.lastIndexOf('#'));
            String[] secs = ymd.split("-");
            String year = secs[0];
            String month = secs[1];
            String day = secs[2];
            String page_no = secs[3];
            for(int i=1;;i++){
                String title = page.getHtml().xpath("//div[@class='main']/div[@class='box']/h2["+i+"]/text()").get();
                String content = page.getHtml().xpath("//div[@class='main']/div[@class='box']/div[@class='article']["+i+"]/html()").get();
                if(title==null){
                    log.info(year+"年"+month+"月"+day+"日，第"+page_no+"版的人民日报共"+(i-1)+"篇文章");
                    break;
                }
                log.info(i+":"+title);
                Article article = new Article();
                article.setYear(year);
                article.setMonth(month);
                article.setDay(day);
                article.setPage(page_no);
                article.setTitle(title);
                article.setContent(content);
                articles.add(article);
            }
            page.putField("YEAR",year);//年
            page.putField("MONTH",month);//月
            page.putField("DAY",day);//日
            page.putField("PAGE",page_no);//版面
            page.putField("ARTICLE_NUMBER", articles.size());//本版文章数
            page.putField("ARTICLES",articles);//所有文章
        }
    }

    @Override
    public Site getSite() {
        return this.site;
    }
}
