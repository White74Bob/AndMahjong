package wb.game.mahjong;

import java.util.ArrayList;

import android.app.Activity;
import android.content.Context;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;
import wb.game.mahjong.MahjongManager.Position;
import wb.game.mahjong.constants.TileResources.TileType;
import wb.game.mahjong.model.GameResource.Action;
import wb.game.mahjong.model.Tile;
import wb.game.utils.Utils;

public class CustomizeSoundActivity extends Activity {
    private static enum SoundType {
        Action,
        Tile,
        Other;
    }

    private static class SoundItem {
        public final SoundType type;

        public final Action action;
        public final Tile tile;
        public final String other;

        public SoundItem(Action action) {
            type = SoundType.Action;
            this.action = action;
            this.tile = null;
            this.other = null;
        }

        public SoundItem(Tile tile) {
            type = SoundType.Tile;
            this.action = null;
            this.tile = tile;
            this.other = null;
        }

        public SoundItem(String other) {
            type = SoundType.Other;
            this.action = null;
            this.tile = null;
            this.other = other;
        }

        public int getImageResource() {
            if (tile == null) return -1;
            return tile.tileType.getResourceId(tile.tileIndex, Position.BOTTOM.ordinal());
        }
    }

    private final ArrayList<SoundItem> mSoundItems = new ArrayList<SoundItem>();

    private ListView mListSoundItems;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.customize_sound_main);

        initSoundItems();
        initViews();
    }

    private void initSoundItems() {
        SoundItem soundItem;
        for (Action action : Action.values()) {
            if (!action.isVisible()) continue;
            if (action == Action.DetermineIgnored) continue;
            soundItem = new SoundItem(action);
            mSoundItems.add(soundItem);
        }
        //mSoundItems.add(new SoundItem(getString(R.string.gang_black)));

        for (TileType type : TileType.values()) {
            int typeTileNum = type.getCount();
            for (int index = 0; index < typeTileNum; index++) {
                soundItem = new SoundItem(new Tile(type, index));
                mSoundItems.add(soundItem);
            }
        }
    }

    private void initViews() {
        mListSoundItems = (ListView)findViewById(R.id.sound_list);
        mListSoundItems.setAdapter(new SoundListAdapter(this, R.layout.customize_sound_item));
    }

    class SoundListAdapter extends ArrayAdapter<SoundItem> {
        private final int mResourceId;

        private final LayoutInflater mInflater;

        public SoundListAdapter(Context context, int textViewResourceId) {
            super(context, textViewResourceId);
            mResourceId = textViewResourceId;
            mInflater = (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        }

        @Override
        public int getCount() {
            return mSoundItems.size();
        }

        @Override
        public SoundItem getItem(int position) {
            return mSoundItems.get(position);
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            final SoundItem soundItem = getItem(position);
            View view;
            if (convertView == null) {
                view = mInflater.inflate(mResourceId, parent, false);
            } else {
                view = convertView;
            }

            TextView actionText = (TextView) view.findViewById(R.id.text_action);
            if (soundItem.type == SoundType.Action) {
                actionText.setText(soundItem.action.actionResId);
                actionText.setVisibility(View.VISIBLE);
            } else if (soundItem.type == SoundType.Other) {
                actionText.setText(soundItem.other);
                actionText.setVisibility(View.VISIBLE);
            } else {
                actionText.setText(null);
                actionText.setVisibility(View.GONE);
            }

            ImageView tileImageView = (ImageView)view.findViewById(R.id.image_tile);
            if (soundItem.type == SoundType.Action) {
                //tileImageView.setImageResource(null);
                tileImageView.setVisibility(View.GONE);
            } else {
                tileImageView.setImageResource(soundItem.getImageResource());
                tileImageView.setVisibility(View.VISIBLE);
            }

            Button buttonPlaySound = (Button)view.findViewById(R.id.button_play_sound);
            buttonPlaySound.setOnClickListener(new OnClickListener() {
                @Override
                public void onClick(View v) {
                    Utils.showInfo(CustomizeSoundActivity.this, getString(R.string.label_prompt),
                                    "Not implemented!");
                }
            });
            Button buttonDeleteSound = (Button)view.findViewById(R.id.button_delete_sound);
            buttonDeleteSound.setOnClickListener(new OnClickListener() {
                @Override
                public void onClick(View v) {
                    Utils.showInfo(CustomizeSoundActivity.this, getString(R.string.label_prompt),
                                    "不信没有实现呢?!");
                }
            });
            Button buttonRecordSound = (Button)view.findViewById(R.id.button_record_sound);
            buttonRecordSound.setOnClickListener(new OnClickListener() {
                @Override
                public void onClick(View v) {
                    Utils.showInfo(CustomizeSoundActivity.this, getString(R.string.label_prompt),
                                    "不好意思!真没实现!");
                }
            });

            return view;
        }
    }
}
