package me.piebridge.bible;

import android.os.Bundle;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentStatePagerAdapter;
import android.util.SparseArray;
import android.view.ViewGroup;

import static me.piebridge.bible.BaseReadingActivity.POSITION;

/**
 * Created by thom on 16/7/2.
 */
public class ReadingAdapter extends FragmentStatePagerAdapter {

    private int mSize;

    private final SparseArray<ReadingFragment> mFragments;

    public ReadingAdapter(FragmentManager fm, int size) {
        super(fm);
        mSize = size;
        mFragments = new SparseArray<ReadingFragment>();
    }

    public void setSize(int size) {
        mSize = size;
    }

    @Override
    public ReadingFragment getItem(int position) {
        ReadingFragment fragment = mFragments.get(position);
        if (fragment == null) {
            fragment = new ReadingFragment();
            Bundle bundle = new Bundle();
            bundle.putInt(POSITION, position);
            fragment.setArguments(bundle);
            mFragments.put(position, fragment);
        }
        return fragment;
    }

    @Override
    public int getCount() {
        return mSize;
    }

    @Override
    public void destroyItem(ViewGroup container, int position, Object object) {
        super.destroyItem(container, position, object);
        mFragments.remove(position);
    }

}
