package wb.game.mahjong;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;

import android.app.Activity;
import android.content.Intent;
import android.content.res.AssetManager;
import android.os.AsyncTask;
import android.os.Bundle;
import android.widget.TextView;
import wb.game.mahjong.constants.Constants;
import wb.game.mahjong.model.GameResource;
import wb.game.mahjong.model.GameResource.Game;

public class GameIntroductionActivity extends Activity {
    private Game mGame;

    private TextView mTextIntroduction;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.game_introduction);

        Intent intent = getIntent();
        Bundle bundle = intent == null ? null : intent.getExtras();
        int gameIndex = bundle == null ? null : bundle.getInt(Constants.EXTRA_GAME_INDEX);
        mGame = GameResource.getGame(gameIndex);

        setTitle(mGame.labelResId);

        mTextIntroduction = (TextView)findViewById(R.id.text_introduction);

        readFileAsync();
    }

    private void readFileAsync() {
        new AsyncTask<Void, Integer, String>() {
            @Override
            protected void onPreExecute() {
            }

            @Override
            protected String doInBackground(Void... arg0) {
                return readFileContentFromAssets(getResources().getAssets(),
                        mGame.introductionFilename);
            }

            @Override
            protected void onProgressUpdate(Integer... progress) {
            }

            @Override
            protected void onPostExecute(String fileContent) {
                mTextIntroduction.setText(fileContent);
            }
        }.execute();
    }

    private static String readFileContentFromAssets(AssetManager assetManager, String fileName) {
        StringBuilder sb = new StringBuilder();
        InputStream is = null;
        try {
            is = assetManager/*getResources().getAssets()*/.open(fileName);
            InputStreamReader inputReader = new InputStreamReader(is);
            BufferedReader bufReader = new BufferedReader(inputReader);
            String line;
            while ((line = bufReader.readLine()) != null) {
                sb.append(line);
                sb.append('\n');
            }
        } catch (Exception e) {
            sb.append(e);
        } finally {
            if (is != null) {
                try {
                    is.close();
                } catch (IOException ioe) {
                }
            }
        }
        return sb.toString();
    }
}
