package icue.com.smarthomeclient.models;

import android.content.Context;
import android.graphics.Bitmap;
import android.support.v4.view.PagerAdapter;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;

import java.io.IOException;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Iterator;

import icue.com.smarthomeclient.R;

import static icue.com.smarthomeclient.models.Utils.decodeFromBase64;
import static icue.com.smarthomeclient.models.Utils.decodeSampledBitmapFromDrawable;

/**
 * Created by Icue on 2017/4/28.
 */

public class CustomPagerAdapter extends PagerAdapter {

    private Deque<Record> history = new ArrayDeque<>();
    private LayoutInflater mLayoutInflater;

    private CustomPagerAdapter(Context context) {
        mLayoutInflater = (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
    }

    public CustomPagerAdapter(Context context, Deque<Record> history) {
        this(context);
        this.history = history;
    }

    @Override
    public int getCount() {
        return history.size();
//        return mResources.length;
    }

    @Override
    public boolean isViewFromObject(View view, Object object) {
        return view == (LinearLayout) object;
    }

    @Override
    public Object instantiateItem(ViewGroup container, int position) {
        View itemView = mLayoutInflater.inflate(R.layout.pager_item, container, false);

        ImageView imageView = (ImageView) itemView.findViewById(R.id.imageView);

        // move to desired index in the deque
        Iterator<Record> it = this.history.descendingIterator();
        for(int i=0; i<position; i++)
            it.next();

        Bitmap bm = null;
        try {
            bm = decodeSampledBitmapFromDrawable(it.next().getImage(), 200, 200);
//            bm = decodeFromBase64(it.next().getImage());
        } catch (IOException e) {
            e.printStackTrace();
        }
        imageView.setImageBitmap(bm);

        container.addView(itemView);

        return itemView;
    }

    @Override
    public void destroyItem(ViewGroup container, int position, Object object) {
        container.removeView((LinearLayout) object);
    }
}