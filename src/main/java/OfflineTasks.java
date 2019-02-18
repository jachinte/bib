import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;
import org.jbibtex.BibTeXDatabase;
import org.jbibtex.BibTeXFormatter;
import org.jbibtex.BibTeXParser;
import org.jbibtex.Key;
import org.jbibtex.ObjectResolutionException;
import org.jbibtex.ParseException;
import org.jbibtex.TokenMgrException;

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
 * @date 2017-08-03
 * @version $Id$
 * @since 0.0.1
 */
public class OfflineTasks {

    /**
     * Default constructor.
     */
    public OfflineTasks() {
    }

    public BibTeXDatabase filteredEntries(final List<String> keys,
        Stream<File> inputs) {
        return this.filteredEntries(
            keys,
            inputs.map(file -> {
                try {
                    System.out.println(file);
                    return this.database(file);
                } catch (ObjectResolutionException | TokenMgrException
                        | FileNotFoundException | ParseException e) {
                    throw new RuntimeException(e);
                }
            })
            .toArray(it -> new BibTeXDatabase[it])
        );
    }

    public BibTeXDatabase filteredEntries(final List<String> keys,
        final BibTeXDatabase... inputs) {
        BibTeXDatabase filtered = new BibTeXDatabase();
        Stream.of(inputs)
            .flatMap(database -> database.getEntries().entrySet().stream())
            .filter(entry -> keys.contains(entry.getKey().toString()))
            .forEach(entry -> filtered.addObject(entry.getValue()));
        return filtered;
    }

    public BibTeXDatabase database(final File bibFile)
        throws ObjectResolutionException, TokenMgrException,
            FileNotFoundException, ParseException {
        return new BibTeXParser().parse(
            new InputStreamReader(
                new FileInputStream(bibFile),
                Charset.forName("UTF-8")
            )
        );
    }

    public void print(final BibTeXDatabase database, final Key key) {
        final AtomicInteger count = new AtomicInteger();
        database.getEntries()
        .entrySet()
        .stream()
        .forEach(
            entry -> {
                if (entry.getValue().getField(key) == null)
                    return;
                System.out.println(
                    String.format(
                        "%d. %s (%s)",
                        count.incrementAndGet(),
                        entry.getValue().getField(key).toUserString(),
                        entry.getKey()
                    )
                );
            }
        );
    }

    public void write(final BibTeXDatabase database,
        final File outputFile) throws IOException {
        System.out.println(
            String.format(
                "Storing data to %s",
                outputFile.getAbsoluteFile()
            )
        );
        new BibTeXFormatter().format(
            database,
            new FileWriter(outputFile)
        );
    }

    public static void main(String[] args) throws Exception {
        final OfflineTasks app = new OfflineTasks();
        final File input = new File("/Users/miguel/Development/repositories/jachinte-candidacy-2018/bibliography/");
//        app.print(
//            app.database(
//                new File(
//                    input,
//                    "main.bib"
//                )
//            ),
//            new Key("title")
//        );
        app.write(
            app.database(
                new File(
                    input,
                    "main.bib"
                )
            ),
            new File(
                input,
                "___main.bib"
            )
        );
//        final File inputDirectory = new File("/Users/miguel/Development/repositories/SLR2017/SLR/steps");
//        final File outputDirectory = new File("/Users/miguel/Desktop");
//        app.write(
//            app.filteredEntries(
//                Files.readAllLines(
//                    Paths.get(
//                        new File(
//                            outputDirectory,
//                            "papers-for-amelia.txt"
//                        ).getAbsolutePath()
//                    )
//                ),
//                Stream.concat(
//                    Stream.of(
//                        new File(
//                             inputDirectory,
//                            "01-initial-search/bib-files"
//                        ).listFiles(
//                            (dir, name) -> name.endsWith("-2010.bib")
//                        )
//                    ),
//                    Stream.of(
//                        new File(
//                            inputDirectory,
//                            "02-include-cloud-standards/bib-files"
//                        ).listFiles(
//                            (dir, name) -> name.endsWith("-2010.bib")
//                        )
//                    )
//                )
//            ),
//            new File(
//                outputDirectory,
//                "papers-for-amelia.bib"
//            )
//        );
    }

}
