package ir.mokhatabyar.transfer;
import org.junit.Test;
import java.util.*;
import static org.junit.Assert.*;

public class DuplicateAnalyzerTest {
    @Test public void iranFormats() {
        for (String n : Arrays.asList("09123456789","+989123456789","00989123456789","989123456789","۰۹۱۲۳۴۵۶۷۸۹")) {
            assertEquals("989123456789", DuplicateAnalyzer.normalize(n));
        }
    }
    @Test public void skipsExistingAndFlagsMixed() {
        DuplicateAnalyzer a = new DuplicateAnalyzer(Collections.singleton("989123456789"));
        assertEquals(DuplicateAnalyzer.Choice.DUPLICATE, a.classify(Arrays.asList("09123456789")));
        assertEquals(DuplicateAnalyzer.Choice.REVIEW, a.classify(Arrays.asList("09123456789","09120000000")));
        assertEquals(DuplicateAnalyzer.Choice.REVIEW, a.classify(Arrays.asList("09120000000")));
    }
    @Test public void ignoresRepeatedInFile() {
        DuplicateAnalyzer a = new DuplicateAnalyzer(Collections.emptySet());
        assertEquals(DuplicateAnalyzer.Choice.ADD, a.classify(Arrays.asList("09121111111")));
        a.accept(Arrays.asList("09121111111"));
        assertEquals(DuplicateAnalyzer.Choice.DUPLICATE, a.classify(Arrays.asList("+989121111111")));
        assertEquals(DuplicateAnalyzer.Choice.REVIEW, a.classify(Collections.emptyList()));
    }
}
