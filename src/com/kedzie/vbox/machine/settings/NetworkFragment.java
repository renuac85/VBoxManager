package com.kedzie.vbox.machine.settings;


import java.util.ArrayList;

import android.os.Bundle;
import android.support.v4.app.FragmentTabHost;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import com.actionbarsherlock.app.SherlockFragment;
import com.kedzie.vbox.R;
import com.kedzie.vbox.api.IMachine;
import com.kedzie.vbox.api.INetworkAdapter;
import com.kedzie.vbox.app.BundleBuilder;
import com.kedzie.vbox.task.ActionBarTask;

public class NetworkFragment extends SherlockFragment {
    private FragmentTabHost mTabHost;
    
    private IMachine _machine;
    private ArrayList<INetworkAdapter> _adapters;
    
    public static class DummyFragment extends SherlockFragment {
    	public DummyFragment() {
    		super();
    	}
    }
    
    /**
     * Load all the network adapters and create a tab for each one
     */
    class LoadDataTask extends ActionBarTask<IMachine, ArrayList<INetworkAdapter>> {
    	
    	public LoadDataTask() {
    		super(getSherlockActivity(), _machine.getAPI());
    	}
    	
    	@Override
    	protected ArrayList<INetworkAdapter> work(IMachine... params) throws Exception {
    		int maxNumAdapters = 4;
    		ArrayList<INetworkAdapter> adapters = new ArrayList<INetworkAdapter>(maxNumAdapters);
    		for(int i=0; i<maxNumAdapters; i++) 
    			adapters.add(params[0].getNetworkAdapter(i));
    		Log.d(TAG, "Loaded " + adapters.size() + " network adapters");
    		return adapters;
    	}
    	
    	@Override
    	protected void onSuccess(ArrayList<INetworkAdapter> result) {
    		super.onSuccess(result);
    		_adapters = result;
    		populate();
    	}
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
    	super.onSaveInstanceState(outState);
    	outState.putParcelableArrayList("adapters", _adapters);
    }
    
    @Override
    public void onCreate(Bundle savedInstanceState) {
    	super.onCreate(savedInstanceState);
    	_machine = (IMachine)getArguments().getParcelable(IMachine.BUNDLE);
    	if(savedInstanceState!=null)
    		_adapters = savedInstanceState.getParcelableArrayList("adapters");
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_tabs, container, false);
        mTabHost = (FragmentTabHost)view.findViewById(android.R.id.tabhost);
        mTabHost.setup(getActivity(), getChildFragmentManager(), R.id.realtabcontent);
        mTabHost.addTab(mTabHost.newTabSpec("dummy").setIndicator("Dummy"), DummyFragment.class, new Bundle());
        return view;
    }
    
    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
    	super.onActivityCreated(savedInstanceState);
    	if(_adapters==null)
    		new LoadDataTask().execute(_machine);
    	else
			populate();
    }
    
    void populate() {
    	mTabHost.clearAllTabs();
    	for(int i=0; i<_adapters.size(); i++)
			mTabHost.addTab(mTabHost.newTabSpec("adapter#"+i).setIndicator("Adapter " + i), NetworkAdapterFragment.class, 
					new BundleBuilder().putParcelable(INetworkAdapter.BUNDLE, _adapters.get(i)).create());
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        mTabHost = null;
    }
}
