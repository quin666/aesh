package org.aesh.parser;

import java.util.List;

public class ParsedLineWords {

    public static ParsedWord lastWord(List<ParsedWord> words) {
        return words.get(words.size()-1);
    }

    public static ParsedWord firstWord(List<ParsedWord> words) {
        if(words.size() > 0 )
            return words.get(0);
        else
            return new ParsedWord("", 0);
    }
}
