package com.example.portuguesetranslationmockapp;

import android.os.AsyncTask;
import android.os.StrictMode;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.ToggleButton;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.io.IOException;
import java.util.ArrayList;

/**
 * This class is the main activity representing the translation interface for utilizing OpenWord.net
 * resources for translating phrases between English and Portuguese.
 * It supports the following options when translating a word/phrase:
 * 1.   English to Portuguese [or] Portuguese to English
 * 2.   Native Search [or] Priority Search
 * @author Paul Shao
 */
public class MainActivity extends AppCompatActivity implements View.OnClickListener {

    private EditText input;
    private TextView result;
    private Button translate;

    private TextView subtitle;
    private ToggleButton translationOptionToggle;
    private ToggleButton searchOptionToggle;


    private String translationOption;
    private String searchOption;

    private enum translationOptions {
        EN_TO_PT,
        PT_TO_EN
    }

    private enum searchOptions {
        VANILLA,
        PRIORITY
    }

    private static final String VANNILA_SEARCH_URL = "http://wnpt.sl.res.ibm.com/wn/search?term=%s";
    private static final String BASE_SEARCH_URL = "http://wnpt.sl.res.ibm.com/wn/search?fq_word_count_pt=%d&term=%s";
    private static final String CONCEPT_SEARCH_URL = "http://wnpt.sl.res.ibm.com/wn/search?fq_rdftype=%s&term=%s";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        StrictMode.ThreadPolicy policy = new StrictMode.ThreadPolicy.Builder().permitAll().build();
        StrictMode.setThreadPolicy(policy);
        input = findViewById(R.id.input);
        translate = findViewById(R.id.translate);
        result = findViewById(R.id.result);
        subtitle = findViewById(R.id.recommended_translation_subtitle);

        translationOptionToggle = findViewById(R.id.language_support_toggle);
        searchOptionToggle = findViewById(R.id.search_mechanism_toggle);

        // By default
        translationOption = String.valueOf(translationOptions.EN_TO_PT);
        searchOption = String.valueOf(searchOptions.VANILLA);

        translationOptionToggle.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                boolean isSearchOptionChecked = searchOptionToggle.isChecked();
                if (isChecked) {
                    translationOption = String.valueOf(translationOptions.PT_TO_EN);
                    System.out.println("Translation Option changed to: " + translationOption);
                    input.setHint(getResources().getString(R.string.enter_text_pt));
                    translate.setText(getResources().getString(R.string.translate_pt));
                    searchOptionToggle.setTextOn(getResources().getString(R.string.priority_search_pt));
                    searchOptionToggle.setTextOff(getResources().getString(R.string.native_search_pt));
                    searchOptionToggle.setChecked(isSearchOptionChecked);
                    if (searchOption.equals(String.valueOf(searchOptions.VANILLA))) {
                        subtitle.setText(getResources().getString(R.string.recommend_translation_description_vanilla_pt));
                    } else  {
                        subtitle.setText(getResources().getString(R.string.recommend_translation_description_pt));
                    }
                } else {
                    translationOption = String.valueOf(translationOptions.EN_TO_PT);
                    System.out.println("Translation Option changed to: " + translationOption);
                    input.setHint(getResources().getString(R.string.enter_text));
                    translate.setText(getResources().getString(R.string.translate));
                    searchOptionToggle.setTextOn(getResources().getString(R.string.priority_search));
                    searchOptionToggle.setTextOff(getResources().getString(R.string.native_search));
                    searchOptionToggle.setChecked(isSearchOptionChecked);
                    if (searchOption.equals(String.valueOf(searchOptions.VANILLA))) {
                        subtitle.setText(getResources().getString(R.string.recommend_translation_description_vanilla));
                    } else  {
                        subtitle.setText(getResources().getString(R.string.recommend_translation_description));
                    }
                }
            }
        });

        searchOptionToggle.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                if (isChecked) {
                    searchOption = String.valueOf(searchOptions.PRIORITY);
                    // System.out.println("Search Option changed to: " + searchOption);
                    if (translationOption.equals(String.valueOf(translationOptions.PT_TO_EN))) {
                        subtitle.setText(getResources().getString(R.string.recommend_translation_description_pt));
                    } else  {
                        subtitle.setText(getResources().getString(R.string.recommend_translation_description));
                    }
                } else {
                    searchOption = String.valueOf(searchOptions.VANILLA);
                    // System.out.println("Search Option changed to: " + searchOption);
                    if (translationOption.equals(String.valueOf(translationOptions.PT_TO_EN))) {
                        subtitle.setText(getResources().getString(R.string.recommend_translation_description_vanilla_pt));
                    } else  {
                        subtitle.setText(getResources().getString(R.string.recommend_translation_description_vanilla));
                    }
                }
            }
        });


        translate.setOnClickListener(this);
    }

    @Override
    public void onClick(View v) {
        switch (v.getId()) {
            case R.id.translate:
                String originalWord = input.getText().toString();
                if (originalWord == null || originalWord.trim().length() == 0) {
                    Toast.makeText(this, "Input cannot be empty.", Toast.LENGTH_LONG).show();
                } else {
                    String []args = {originalWord};
                    (new fetchTranslationTask()).execute(args);
                    // DO SOMETHING
                }
                break;

        }

    }

    /**
     * This helper method helps locate the modifier for the translation.
     * A modifier of a word is defined as the context the word is representing, such as
     * a noun (n.), a verb (v.), an adjective (a.), etc.
     * @param listOfMatches the list of matches given a regex/parsing pattern
     * @param currentIndex the current index to locate the modifier within listOfMatches
     * @return a properly formatted modifier
     */
    private String locateModifierOfWord(Elements listOfMatches, int currentIndex) {
        String modifier = listOfMatches.get(currentIndex).text().trim();
        System.out.println("Modifier: " + modifier);
        return "(" + modifier.substring(modifier.length() - 1) + ".)";
    }

    /**
     * This method helps perform a vanilla search directly based on a given word.
     * Depending on the direction of translation, it will return different results.
     * @param query the query to search for translations within OpenWord.Net
     * @return a list of proper translations (max 5 elements)
     */
    private ArrayList<String> vannilaSearch(String query) {
        System.out.println("Triggering vannila search on query: " + query);
        ArrayList<String> curr_results = new ArrayList<>();
        try {
            Document vanila_webpage = Jsoup.connect(String.format(VANNILA_SEARCH_URL, query)).get();
            int modifierIndex = 0;
            Elements result_modifier = vanila_webpage.select("a[href~=^synset]");
            if (translationOption.equals(String.valueOf(translationOptions.EN_TO_PT))) {
                Elements result_list = vanila_webpage.select("b");
                for (String translation: result_list.eachText()) {
                    if (translation.contains("|") && translation.substring(2).trim().length() >= 2) {
                        System.out.println("Translation: " + translation);
                        String portuguesTranslation = translation.substring(2);
                        curr_results.add(portuguesTranslation + " " + locateModifierOfWord(result_modifier, modifierIndex));
                    }
                    if (curr_results.size() >= 5) {
                        return curr_results;
                    }
                    modifierIndex += 1;
                }
            } else {
                Elements result_list = vanila_webpage.select("li[class$=synset]");
                for (Element individual_div: result_list) {
                    String englishTranslation = individual_div.ownText();
                    if (englishTranslation.trim().length() > 2) {
                        System.out.println("Translation: " + englishTranslation);
                        curr_results.add(englishTranslation + " " + locateModifierOfWord(result_modifier, modifierIndex));
                    }
                    if (curr_results.size() >= 5) {
                        return curr_results;
                    }
                    modifierIndex += 1;
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return curr_results;
    }

    /**
     * This method performs a priority (more advanced) search for a given query and returns
     * the proper translation depending on the direction of translation.
     * @param query the word to search for translations within OpenWord.Net
     * @return a list of proper translations (max 5 elements)
     */
    private ArrayList<String> prioritySearch(String query) {
        System.out.println("Triggering priority search on query: " + query);

        ArrayList<String> curr_results = new ArrayList<>();
        String []concepts = {"BaseConcept", "CoreConcept"};
        // Attempting base and core search
        for (String concept: concepts) {
            try {
                Document webpage = Jsoup.connect(String.format(CONCEPT_SEARCH_URL, concept, query.toLowerCase())).get();
                Elements list = webpage.select("b");
                if (!list.isEmpty()) {
                    for (String result : list.eachText()) {
                        if (result.contains("|")) {
                            curr_results.add(result.substring(2));
                        }
                        if (curr_results.size() >= 5) {
                            return curr_results;
                        }
                    }
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        // If base and core searches don't work, try fundamental search
        for (int i = 5; i >= 1; i--){
            String current_search = String.format(BASE_SEARCH_URL, i, query);
            System.out.println("Current Search Request: " + current_search);
            try {
                Document webpage = Jsoup.connect(current_search + query.toLowerCase()).get();
                Elements list = webpage.select("b");
                if (list.isEmpty()) {
                    continue;
                }
                for (String result : list.eachText()) {
                    if (result.contains("|")) {
                        curr_results.add(result.substring(2));
                    }
                    if (curr_results.size() >= 5) {
                        return curr_results;
                    }
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        return curr_results;
    }

    private class fetchTranslationTask extends AsyncTask<String, ArrayList<String>, ArrayList<String>> {

        @Override
        protected ArrayList<String> doInBackground(String... originalWord) {
            String first_query = originalWord[0];
            ArrayList<String> translations;
            if (searchOption.equals(String.valueOf(searchOptions.VANILLA))) {
                translations = vannilaSearch(first_query);
            } else {
                translations = prioritySearch(first_query);
            }
            System.out.println(translations);
            return translations;

        }

        @Override
        protected void onPostExecute(ArrayList<String> translations) {
            String actual_result = "";
            for (int i = 0; i < translations.size(); i++) {
                actual_result += i + ". " + translations.get(i) + "\n";
            }
            System.out.println(actual_result);
            if (actual_result.isEmpty()) {
                if (translationOption.equals(String.valueOf(translationOptions.EN_TO_PT))) {
                    result.setText(getResources().getString(R.string.word_not_find));
                } else {
                    result.setText(getResources().getString(R.string.word_not_find_pt));
                }
            } else {
                result.setText(actual_result);
            }
        }
    }

}
