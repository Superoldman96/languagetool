/* LanguageTool, a natural language style checker 
 * Copyright (C) 2005 Daniel Naber (http://www.danielnaber.de)
 * 
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA  02110-1301
 * USA
 */
package de.danielnaber.languagetool.rules.patterns;

import java.util.ArrayList;
import java.util.List;
import java.io.IOException;

import de.danielnaber.languagetool.AnalyzedSentence;
import de.danielnaber.languagetool.AnalyzedToken;
import de.danielnaber.languagetool.AnalyzedTokenReadings;
import de.danielnaber.languagetool.Language;
import de.danielnaber.languagetool.rules.Rule;
import de.danielnaber.languagetool.rules.RuleMatch;
import de.danielnaber.languagetool.tools.StringTools;

/**
 * A Rule that describes a language error as a simple pattern of words or of part-of-speech tags.
 * 
 * @author Daniel Naber
 */
public class PatternRule extends Rule {

  private String id;

  private Language[] language;
  private String description;
  private String message;

  private int startPositionCorrection = 0;
  private int endPositionCorrection = 0;
 
  private List<Element> patternElements;
  
  /** Formatted suggestion elements **/  
  private List<Match> suggestionMatches;

  /** Marks whether the rule is a member of a 
   * disjunctive set (in case of OR operation on phraserefs). 
   **/
  private boolean isMemberOfDisjunctiveSet = false;
  
  /**
   * @param id Id of the Rule
   * @param language Language of the Rule
   * @param elements Element (token) list
   * @param description Description to be shown (name)
   * @param message Message to be displayed to the user
   */
  
  PatternRule(final String id, final Language language, final List<Element> elements, final String description,
      final String message) {
    if (id == null)
      throw new NullPointerException("id cannot be null");
    if (language == null)
      throw new NullPointerException("language cannot be null");
    if (elements == null)
      throw new NullPointerException("elements cannot be null");
    if (description == null)
      throw new NullPointerException("description cannot be null");
    this.id = id;
    this.language = new Language[] { language };
    this.description = description;
    this.message = message;
    this.patternElements = new ArrayList<Element>(elements); // copy elements
  }

  PatternRule(final String id, final Language language, final List<Element> elements, final String description,
      final String message, final boolean isMember) {
    if (id == null)
      throw new NullPointerException("id cannot be null");
    if (language == null)
      throw new NullPointerException("language cannot be null");
    if (elements == null)
      throw new NullPointerException("elements cannot be null");
    if (description == null)
      throw new NullPointerException("description cannot be null");
    this.id = id;
    this.language = new Language[] { language };
    this.description = description;
    this.message = message;
    this.patternElements = new ArrayList<Element>(elements); // copy elements
    this.isMemberOfDisjunctiveSet = isMember;    
  }  
  
  public final String getId() {
    return id;
  }

  public final String getDescription() {
    return description;
  }

  public final String getMessage() {
    return message;
  }

  /** Used for testing rules: only one of the set
   * can match.
   * @return Whether the rule can non-match (as a member of 
   * disjunctive set of rules generated by phraseref in
   * includephrases element).
   */
  public final boolean isWithComplexPhrase() {
    return isMemberOfDisjunctiveSet;
  }
  
  /** Reset complex status - used for testing. **/
  public final void notComplexPhrase() {
    isMemberOfDisjunctiveSet = false;
  }
  
  public final Language[] getLanguages() {
    return language.clone();
  }

  public final String toString() {
    return id + ":" + patternElements + ":" + description;
  }

  public final void setMessage(final String message) {
    this.message = message;
  }
  
  public final void setStartPositionCorrection(final int startPositionCorrection) {
    this.startPositionCorrection = startPositionCorrection;
  }  

  public final void setEndPositionCorrection(final int endPositionCorrection) {
    this.endPositionCorrection = endPositionCorrection;
  }
  
  
  public final RuleMatch[] match(final AnalyzedSentence text) throws IOException {
            
    List<RuleMatch> ruleMatches = new ArrayList<RuleMatch>();    
    final AnalyzedTokenReadings[] tokens = text.getTokensWithoutWhitespace();
    int[] tokenPositions = new int[tokens.length + 1 ];
    
    int tokenPos = 0;
    int prevSkipNext = 0;
    int skipNext = 0;
    int matchPos = 0;
    int skipShift = 0;
    // this variable keeps the total number
    // of tokens skipped - used to avoid
    // that nextPos gets back to unmatched tokens...
    int skipShiftTotal = 0;

    int firstMatchToken = -1;
    int lastMatchToken = -1;
    int patternSize = patternElements.size();
    Element elem = null, prevElement = null;
    final boolean startWithSentStart = patternElements.get(0).isSentStart();

    for (int i = 0; i < tokens.length; i++) {
      boolean allElementsMatch = true;

      //stop processing if rule is longer than the sentence
      if (patternSize + i > tokens.length) {
        allElementsMatch = false;
        break;
      }

      //stop looking for sent_start - it will never match any
      //token except the first
      if (startWithSentStart && i > 0) {
        allElementsMatch = false;
        break;
      }

      int matchingTokens = 0;
      for (int k = 0; (k < patternSize); k++) {
        if (elem != null) {
          prevElement = elem;
        }
        elem = patternElements.get(k);
        skipNext = elem.getSkipNext();
        int nextPos = tokenPos + k + skipShiftTotal;
        if (nextPos >= tokens.length) {
          allElementsMatch = false;
          break;
        }

        boolean skipMatch = false, thisMatched = false, prevMatched = false;
        boolean exceptionMatched = false;
        if (prevSkipNext + nextPos >= tokens.length || prevSkipNext < 0) { // SENT_END?
          prevSkipNext = tokens.length - (nextPos + 1);
        }
        for (int m = nextPos; m <= nextPos + prevSkipNext; m++) {
          boolean matched = false;
          int numberOfReadings = tokens[m].getReadingsLength();

          for (int l = 0; l < numberOfReadings; l++) {
            AnalyzedToken matchToken = tokens[m].getAnalyzedToken(l);
            if (prevSkipNext > 0 && prevElement != null) {
              if (prevElement.prevExceptionMatch(matchToken)) {
                exceptionMatched = true;
                prevMatched = true;
              }
            }
            thisMatched |= elem.match(matchToken);
            exceptionMatched |= elem.exceptionMatch(matchToken);
            // Logical OR (cannot be AND):
            if (!thisMatched && !exceptionMatched) {
              matched |= false;
            } else {
              matched = true;
              matchPos = m;
              skipShift = matchPos - nextPos;              
              tokenPositions[matchingTokens] = skipShift + 1;              
            }
            skipMatch = (skipMatch || matched) && !exceptionMatched;
          }
          
          //disallow exceptions that should match only current tokens          
          if (!thisMatched && !prevMatched) {
            exceptionMatched = false;
          }
                    
          if (skipMatch) {
            break;
          }
          
        }
        //disallow exceptions that should match only current tokens        
        if (!thisMatched && !prevMatched) {
          skipMatch = false;
        }
        allElementsMatch = skipMatch;
        if (skipMatch) {
          prevSkipNext = skipNext;
        } else {
          prevSkipNext = 0;
        }
        if (allElementsMatch) {                              
          matchingTokens++;
          lastMatchToken = matchPos; // nextPos;          
          if (firstMatchToken == -1)
            firstMatchToken = matchPos; // nextPos;
          skipShiftTotal += skipShift;
        } else {
          skipShiftTotal = 0;
          break;
        }
      }
      
      tokenPos++;
      
      if (allElementsMatch) {
        String errMessage = formatMatches(tokens,
            tokenPositions, firstMatchToken, matchingTokens,
            message);
                
        int correctedStPos = 0;
        if (startPositionCorrection > 0) {        
        for (int l = 0; l <= startPositionCorrection; l++) {
          correctedStPos +=  tokenPositions[l];
        }
        correctedStPos--;
        }        
        
        int correctedEndPos = 0;
        if (endPositionCorrection < 0) {
          int l = 0;
          while (l > endPositionCorrection) {
            int test = matchingTokens + l - 1;
            test = tokenPositions[test];
            correctedEndPos -= tokenPositions[matchingTokens + l - 1];
            l--;
          }
          }         
        
        AnalyzedTokenReadings firstMatchTokenObj = tokens[firstMatchToken + correctedStPos];
        boolean startsWithUppercase = StringTools.startsWithUppercase(firstMatchTokenObj.toString());
        if (firstMatchTokenObj.isSentStart() && tokens.length > firstMatchToken + correctedStPos + 1) {
          // make uppercasing work also at sentence start: 
          firstMatchTokenObj = tokens[firstMatchToken + correctedStPos + 1];
          startsWithUppercase = StringTools.startsWithUppercase(firstMatchTokenObj.toString());
        }
        int fromPos = tokens[firstMatchToken + correctedStPos]
                             .getStartPos();
        int toPos = tokens[lastMatchToken + correctedEndPos].getStartPos()
        + tokens[lastMatchToken + correctedEndPos].getToken().length();
        if (fromPos < toPos) { //this can happen with some skip="-1" when the last token is not matched
        RuleMatch ruleMatch = new RuleMatch(this, fromPos, toPos, errMessage,
            startsWithUppercase);        
          ruleMatches.add(ruleMatch);        
        }
      } else {
        firstMatchToken = -1;
        lastMatchToken = -1;
        skipShiftTotal = 0;
      }
    }

    return (RuleMatch[]) ruleMatches.toArray(new RuleMatch[ruleMatches.size()]);
  }

  public void addSuggestionMatch(final Match m) {
    if (suggestionMatches == null) {
      suggestionMatches = new ArrayList<Match>();      
    }
    suggestionMatches.add(m);
  }
   
  /** Replace back references generated with &lt;match&gt; and \\1 
   *  in message using Match class, and take care of skipping.
  *    @return String Formatted message. 
  **/
  public final String formatMatches(final AnalyzedTokenReadings[] toks,
      final int[] positions, final int firstMatchTok, int matchingTok,
      final String errorMsg) throws IOException {
    String errorMessage = errorMsg;
    // replace back references like \1 in message, 
    // and take care of skipping 
    if (firstMatchTok + matchingTok >= toks.length) {
      matchingTok = toks.length - firstMatchTok;
    }
    int matchCounter = 0;
    boolean newWay = false;
    int errLen = errorMessage.length();
    int errMarker = errorMessage.indexOf("\\");
    boolean numberFollows = false;
    if (errMarker > 0 & errMarker < errLen - 1) {
      numberFollows = errorMessage.charAt(errMarker + 1) >= '1'
        & errorMessage.charAt(errMarker + 1) <= '9';
    }
    while (errMarker > 0 & numberFollows) {
      for (int j = 0; j < matchingTok; j++) {
        int repTokenPos = 0;
        for (int l = 0; l <= j; l++) {
          repTokenPos += positions[l];
        }
        int ind = errorMessage.indexOf("\\" + (j + 1)); 
        if (ind > 0) {      
          if (suggestionMatches != null) {
            if (matchCounter < suggestionMatches.size()) {
              if (suggestionMatches.get(matchCounter) != null) {             
                suggestionMatches.get(matchCounter)
                .setToken(toks[firstMatchTok + repTokenPos - 1]);
                suggestionMatches.get(matchCounter).setSynthesizer(language[0].getSynthesizer());              
                String leftSide = errorMessage.substring(0, ind);
                String suggestionLeft = "";
                String suggestionRight = "";
                String rightSide = errorMessage.substring(ind + 2);
                String[] matches = suggestionMatches.get(matchCounter).toFinalString();
                if (matches.length == 1) {
                  errorMessage = leftSide 
                  + suggestionLeft
                  + matches[0]
                            + suggestionRight
                            + rightSide;              
                } else {
                  errorMessage = leftSide;
                  int lastLeftSugEnd = leftSide.indexOf("</suggestion>");
                  int lastLeftSugStart = leftSide.lastIndexOf("<suggestion>");
                  for (String formatMatch : matches) {
                    errorMessage += suggestionLeft
                    + formatMatch 
                    + suggestionRight;
                    if (lastLeftSugEnd == -1 && lastLeftSugStart > 0) {
                      errorMessage += "</suggestion>, <suggestion>";
                    }
                  }
                  int correctionSug = errorMessage.lastIndexOf("<suggestion>");
                  if (correctionSug + "<suggestion>".length() == errorMessage.length())
                    errorMessage = errorMessage.substring(0, correctionSug);
                  errorMessage += rightSide;             
                }
                matchCounter++;
                newWay = true;
              }
            }
          }
        }           
        if (!newWay) {
          errorMessage = errorMessage.replaceAll("\\\\" + (j + 1), 
              toks[firstMatchTok + repTokenPos - 1].getToken());          
        }
      }
      errMarker = errorMessage.indexOf("\\");
      numberFollows = false;
      if (errMarker > 0 & errMarker < errLen - 1) {
        numberFollows = errorMessage.charAt(errMarker + 1) >= '1'
          & errorMessage.charAt(errMarker + 1) <= '9';
      }      
    }
    return errorMessage;
  }
  
  public void reset() {
    // nothing
  }

}
