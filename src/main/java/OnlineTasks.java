import java.io.File;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.channels.Channels;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Map.Entry;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiConsumer;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.jbibtex.BibTeXDatabase;
import org.jbibtex.BibTeXEntry;
import org.jbibtex.BibTeXParser;
import org.jbibtex.Key;
import org.jbibtex.ObjectResolutionException;
import org.jbibtex.ParseException;
import org.jbibtex.StringValue;
import org.jbibtex.TokenMgrException;
import org.jbibtex.Value;
import org.jsoup.Jsoup;
import org.jsoup.helper.StringUtil;
import org.jsoup.nodes.Document;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.chrome.ChromeDriverService;
import org.openqa.selenium.remote.DesiredCapabilities;
import org.openqa.selenium.remote.RemoteWebDriver;

/**
 * Copyright 2017 University of Victoria
 * 
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to
 * deal in the Software without restriction, including without limitation the
 * rights to use, copy, modify, merge, publish, distribute, sublicense, and/or
 * sell copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 * 
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 * 
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING
 * FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS
 * IN THE SOFTWARE.
 */

/**
 * TODO
 * @author Miguel Jimenez (miguel@uvic.ca)
 * @date 2017-07-19
 * @version $Id$
 * @since 0.0.1
 */
public class OnlineTasks {

    /**
     * The Bibtex database.
     */
    private final BibTeXDatabase database;

    /**
     * The web driver instance.
     */
    private final WebDriver webDriver;

    /**
     * Current URL of the Web driver.
     */
    private String currentUrl;

    /**
     * Default constructor.
     * @throws ParseException 
     * @throws TokenMgrException 
     * @throws ObjectResolutionException 
     * @throws IOException 
     */
    public OnlineTasks(final File bibFile)
        throws ObjectResolutionException, TokenMgrException,
            ParseException, IOException {
        final ChromeDriverService service = new ChromeDriverService.Builder()
            .usingDriverExecutable(
                new File(
                    System.getProperty("webdriver.chrome.driver")
                )
            )
            .usingAnyFreePort()
            .build();
        service.start();
        this.webDriver = new RemoteWebDriver(
            service.getUrl(),
            DesiredCapabilities.chrome()
        );
        this.database = new BibTeXParser().parse(
            new FileReader(bibFile)
        );
    }

    private void forEachEntry(final AtomicInteger missingDOIs,
        BiConsumer<String, Entry<Key, BibTeXEntry>> action) {
        this.database.getEntries()
        .entrySet()
        .stream()
        .forEach(
            entry -> {
                final Value DoiField = entry.getValue().getField(BibTeXEntry.KEY_DOI);
                if (DoiField == null) {
                    missingDOIs.incrementAndGet();
                } else {
                    action.accept(DoiField.toUserString(), entry);
                }
            }
        );
    }

    public void findPdfFiles(final File outputDirectory,
        final boolean downloadFiles) throws IOException {
        if (!outputDirectory.exists())
            outputDirectory.mkdirs();
        final File list = new File(outputDirectory, "list.txt");
        if (!list.exists())
            list.createNewFile();
        final List<String> existingKeys = new ArrayList<>();
        if (downloadFiles) {
            existingKeys.addAll(
                Stream.of(outputDirectory.list())
                    .map(name -> name.substring(0, name.lastIndexOf('.')))
                    .collect(Collectors.toList())
            );
        } else {
            existingKeys.addAll(
                Files.lines(Paths.get(list.getAbsolutePath()))
                    .map(line -> {
                        final String name = line.split(" ")[0];
                        return name.substring(0, name.lastIndexOf('.'));
                    })
                    .collect(Collectors.toList())
            );  
        } 
        final AtomicInteger missingDOIs = new AtomicInteger();
        final AtomicInteger notFoundPDFs = new AtomicInteger();
        final FileWriter writer = new FileWriter(list, true);
        this.forEachEntry(
            missingDOIs,
            (doi, entry) -> {
                if (!existingKeys.contains(entry.getKey().toString())) {
                    try {
                        final String link = this.PdfLink(String.format("https://dx.doi.org/%s", doi));
                        writer.append(String.format("%s.pdf %s\n", entry.getKey(), link));
                        writer.flush();
                        if (downloadFiles)
                            this.downloadFile(
                                new File(outputDirectory, String.format("%s.pdf", entry.getKey())), link
                            );
                    } catch (IOException | URISyntaxException e) {
                        notFoundPDFs.incrementAndGet();
                        System.err.printf("%s (%s) couldn't be downloaded\n", entry.getKey(), doi);
                        e.printStackTrace();
                    }
                }
            }
        );
        writer.close();
        System.out.println(
            String.format(
                "%d DOI were not found in the Bibtex library, and %d PDFs "
                + "couldn't be downloaded",
                missingDOIs.get(),
                notFoundPDFs.get()
            )
        );
        if (!downloadFiles)
            System.out.printf(
                "To download the files, change the working directory to '%s'"
                + "\nand execute one of:\n%s\n%s\n",
                outputDirectory.getAbsolutePath(),
                "cat list.txt | awk '{print \"wget -c -O\"$1\" \"$2}' | xargs -0 bash -c",
                "cat list.txt | awk '{print \"curl -L -o \"$1\" -C - \"$2}' | xargs -0 bash -c"
            );
    }

    public void updateAbstracts() {
        final AtomicInteger notFoundCount = new AtomicInteger();
        final AtomicInteger count = new AtomicInteger();
        this.forEachEntry(
            notFoundCount,
            (doi, entry) -> {
                if (entry.getValue().getField(new Key("abstract")) != null)
                    return;
                try {
                    final String _abstract = this.fetchAbstract(doi);
                    if (_abstract.isEmpty())
                        return;
                    entry.getValue().addField(
                        new Key("abstract"),
                        new StringValue(
                            _abstract,
                            StringValue.Style.BRACED
                        )
                    );
                    System.out.println(
                        String.format(
                            "%d. %s: %s",
                            count.incrementAndGet(),
                            entry.getKey(),
                            _abstract
                        )
                    );
                } catch (IOException | URISyntaxException e) {
                    System.err.printf("%s (%s) couldn't be found\n", entry.getKey(), doi);
                    e.printStackTrace();
                }
            }
        );
        System.out.println(
            String.format(
                "%d DOI were not found in the Bibtex library.",
                notFoundCount.get()
            )
        );
    }

    public String textSelector(final String baseUri) throws URISyntaxException {
        switch (new URI(baseUri).getHost()) {
            case "dl.acm.org": return "#abstract p";
            case "ieeexplore.ieee.org": return ".abstract-text";
            case "link.springer.com": return ".Abstract > .Para";
            case "www.sciencedirect.com": return ".abstract.author > div > p";
            case "linkinghub.elsevier.com": return ".abstract.author > div > p"; // same as www.sciencedirect.com
            case "www.scitepress.org": return "#ContentPlaceHolder1_LinkPaperPage_LinkPaperContent_LabelAbstract";
            case "www.igi-global.com": return "#ctl00_ctl00_cphMain_cphSection_lblAbstract";
            case "onlinelibrary.wiley.com": return ".article-section__content mainAbstract > p";
            case "www.inderscience.com": return "#col1 table tbody > tr > td > font";
            case "jserd.springeropen.com": return ".AbstractSection > .Para";
            case "journalofcloudcomputing.springeropen.com": return ".Abstract > .Para";
            case "www.worldscientific.com": return ".NLM_abstract";
            default: {
                System.err.println(
                    String.format(
                        "UNKNOWN URI: %s. Using default selector",
                        baseUri
                    )
                );
                return ".abstract";
            }
        }
    }

    public String linkSelector(final String baseUri)
        throws URISyntaxException {
        switch (new URI(baseUri).getHost()) {
            case "link.springer.com": return "#cobranding-and-download-availability-text > div > a";
            case "dl.acm.org": return "#divmain > table > tbody > tr > td > table > tbody > tr > td > a[name=FullTextPDF]";
            case "ieeexplore.ieee.org": return ".doc-actions > li > .stats-document-lh-action-downloadPdf_2";
            case "www.sciencedirect.com": return ".PdfDropDownMenu > ul > li:first-child > a";
            case "onlinelibrary.wiley.com": return ".article-support__item-link.js-infopane-epdf";
            default: {
                System.err.println(
                    String.format(
                        "UNKNOWN URI: %s. Using default link selector",
                        baseUri
                    )
                );
                return ".download";
            }
        }
    }

    private String postProcessPdfLink(final String url)
        throws URISyntaxException, IOException {
        String postProcessedUrl = new String();
        switch (new URL(url).getHost()) {
            case "ieeexplore.ieee.org": {
               // resolve JavaScript redirect
                this.renderPage(
                    // Resolve frame
                    this.renderPage(url)
                        .select("frameset frame:nth-child(2)")
                        .attr("src")
                );
                postProcessedUrl = this.currentUrl;
            } break;
            case "www.sciencedirect.com": {
                postProcessedUrl = StringUtil.resolve(
                    url,
                    Jsoup.connect(url)
                        .followRedirects(true)
                        .get()
                        .select(".pdf-download-btn-link")
                        .attr("href")
                );
            } break;
            default: {
                postProcessedUrl = url;
            }
        }
        return postProcessedUrl;
    }

    private void downloadFile(final File output, final String source)
        throws IOException {
        HttpURLConnection.setFollowRedirects(true);
        HttpURLConnection connection =
            (HttpURLConnection) new URL(this.currentUrl).openConnection();
        connection.connect();
        final FileOutputStream stream = new FileOutputStream(output.getAbsolutePath());
        stream
            .getChannel()
            .transferFrom(
                Channels.newChannel(connection.getInputStream()),
                0,
                Long.MAX_VALUE
            );
        stream.close();
    }

    public String PdfLink(final String url)
        throws IOException, URISyntaxException {
        Document document = this.renderPage(url);
        return this.postProcessPdfLink(
            StringUtil.resolve(
                this.currentUrl,
                document
                    .select(this.linkSelector(this.currentUrl))
                    .attr("href")
            )
        );
    }

    public String fetchAbstract(final String DOI)
        throws IOException, URISyntaxException {
        return this.renderPage(
            String.format(
                "https://dx.doi.org/%s",
                DOI
            )
        ).select(this.textSelector(this.currentUrl))
         .text();
    }

    public Document renderPage(final String url) throws MalformedURLException {
        new URL(url); // It must be a valid URL
        this.webDriver.get(url);
        this.currentUrl = webDriver.getCurrentUrl();
        return Jsoup.parse(webDriver.getPageSource());
    }

    public void shutdown() {
        this.webDriver.quit();
    }

    // java -Dwebdriver.chrome.driver=libs/chromedriver -jar target/bibtex-abstract-0.0.1-SNAPSHOT-jar-with-dependencies.jar 2>/dev/null
    public static void main(String[] args)
        throws ObjectResolutionException, TokenMgrException, ParseException,
            IOException {
        System.setProperty("webdriver.chrome.driver", "libs/chromedriver");
        final File directory = new File("/Users/miguel/Development/repositories/jachinte-candidacy-2018/bibliography");
        final File bibfile = new File(
            directory,
            "main.bib"
        );
        final OnlineTasks online = new OnlineTasks(bibfile);
//        final OfflineTasks offline = new OfflineTasks();
        online.updateAbstracts();
//        online.findPdfFiles(
//            new File(
//                directory,
//                "PDFs"
//            ),
//            false
//        );
//        offline.write(
//            online.database,
//            new File(
//                directory,
//                "_main.bib"
//            )
//        );
        online.shutdown();
    }

}
