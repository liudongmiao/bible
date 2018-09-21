package me.piebridge.bible.adapter;

import android.os.Bundle;
import android.util.SparseArray;
import android.view.ViewGroup;

import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentStatePagerAdapter;

import me.piebridge.bible.fragment.ReadingFragment;

import static me.piebridge.bible.activity.AbstractReadingActivity.POSITION;

/**
 * Created by thom on 16/7/2.
 */
public class ReadingAdapter extends FragmentStatePagerAdapter {

    private int mSize;

    private final SparseArray<Bundle> mBundles;

    private final SparseArray<ReadingFragment> mFragments;

    public ReadingAdapter(FragmentManager fm, int size) {
        super(fm);
        mSize = size;
        mBundles = new SparseArray<>();
        mFragments = new SparseArray<>();
    }

    public void setSize(int size) {
        mSize = size;
    }

    @Override
    public Object instantiateItem(ViewGroup container, int position) {
        ReadingFragment fragment = (ReadingFragment) super.instantiateItem(container, position);
        mFragments.put(position, fragment);
        return fragment;
    }

    @Override
    public ReadingFragment getItem(int position) {
        ReadingFragment fragment = new ReadingFragment();
        fragment.setArguments(mBundles.get(position));
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
        mBundles.remove(position);
    }

    public ReadingFragment getFragment(int position) {
        return mFragments.get(position);
    }

    public synchronized void setData(int position, Bundle bundle) {
        bundle.putInt(POSITION, position);
        mBundles.put(position, bundle);
    }

    public synchronized Bundle getData(int position) {
        Bundle bundle = mBundles.get(position);
        if (bundle == null) {
            bundle = new Bundle();
            mBundles.put(position, bundle);
        }
        return bundle;
    }

}
