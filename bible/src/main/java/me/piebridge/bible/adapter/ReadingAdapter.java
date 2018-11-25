package me.piebridge.bible.adapter;

import android.os.Bundle;
import android.util.SparseArray;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentStatePagerAdapter;

import me.piebridge.bible.activity.AbstractReadingActivity;
import me.piebridge.bible.fragment.ReadingFragment;
import me.piebridge.bible.utils.BibleUtils;
import me.piebridge.bible.utils.LogUtils;
import me.piebridge.bible.utils.ObjectUtils;

import static me.piebridge.bible.activity.AbstractReadingActivity.OSIS;
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
    public Object instantiateItem(@NonNull ViewGroup container, int position) {
        LogUtils.d("instantiateItem, position " + position);
        ReadingFragment fragment = (ReadingFragment) super.instantiateItem(container, position);
        mFragments.put(position, fragment);
        Bundle arguments = ObjectUtils.requireNonNull(fragment.getArguments());
        Bundle bundle = getData(position);
        if (!bundle.containsKey(OSIS)) {
            LogUtils.w("bundle is null");
        }
        if (arguments.isEmpty()) {
            arguments.putAll(bundle);
        } else if (BibleUtils.putAll(arguments, bundle)) {
            fragment.reloadData();
        }
        return fragment;
    }

    @Override
    public ReadingFragment getItem(int position) {
        ReadingFragment fragment = new ReadingFragment();
        ObjectUtils.requireNonNull(fragment.getArguments()).putAll(getData(position));
        return fragment;
    }

    @Override
    public int getCount() {
        return mSize;
    }

    @Override
    public void destroyItem(@NonNull ViewGroup container, int position, @NonNull Object object) {
        super.destroyItem(container, position, object);
        mFragments.remove(position);
        mBundles.remove(position);
    }

    @Override
    public int getItemPosition(@NonNull Object object) {
        if (object instanceof ReadingFragment) {
            ReadingFragment fragment = (ReadingFragment) object;
            Bundle arguments = fragment.getArguments();
            if (arguments != null && arguments.getInt(AbstractReadingActivity.ID) > mSize) {
                return POSITION_NONE;
            }
        }
        return POSITION_UNCHANGED;
    }

    public ReadingFragment getFragment(int position) {
        return mFragments.get(position);
    }

    public synchronized void setData(int position, Bundle bundle) {
        bundle.putInt(POSITION, position);
        Bundle oldBundle = mBundles.get(position);
        if (oldBundle == null) {
            mBundles.put(position, bundle);
        } else {
            oldBundle.putAll(bundle);
        }
    }

    public synchronized Bundle getData(int position) {
        Bundle bundle = mBundles.get(position);
        if (bundle == null) {
            bundle = new Bundle();
            mBundles.put(position, bundle);
        }
        bundle.putInt(POSITION, position);
        return bundle;
    }

    public void clearData() {
        mBundles.clear();
    }

    public void dump(int position) {
        LogUtils.d("position: " + position + ", data: " + mBundles.get(position));
    }

}
