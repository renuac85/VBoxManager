package com.kedzie.vbox.machine.settings;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.os.Bundle;
import android.util.Log;
import android.view.ContextMenu;
import android.view.ContextMenu.ContextMenuInfo;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.BaseExpandableListAdapter;
import android.widget.ExpandableListView;
import android.widget.ExpandableListView.ExpandableListContextMenuInfo;
import android.widget.ExpandableListView.OnChildClickListener;
import android.widget.ExpandableListView.OnGroupClickListener;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.LinearLayout.LayoutParams;
import android.widget.TextView;

import com.actionbarsherlock.app.SherlockFragment;
import com.actionbarsherlock.view.Menu;
import com.actionbarsherlock.view.MenuInflater;
import com.actionbarsherlock.view.MenuItem;
import com.kedzie.vbox.R;
import com.kedzie.vbox.VBoxApplication;
import com.kedzie.vbox.api.IMachine;
import com.kedzie.vbox.api.IMedium;
import com.kedzie.vbox.api.IStorageController;
import com.kedzie.vbox.api.ISystemProperties;
import com.kedzie.vbox.api.jaxb.ChipsetType;
import com.kedzie.vbox.api.jaxb.DeviceType;
import com.kedzie.vbox.api.jaxb.IMediumAttachment;
import com.kedzie.vbox.api.jaxb.StorageBus;
import com.kedzie.vbox.app.Utils;
import com.kedzie.vbox.task.ActionBarTask;
import com.kedzie.vbox.task.DialogTask;

/**
 * 
 */
public class StorageListFragment extends SherlockFragment {

	/**
	 * Listener for Storage Controller clicks
	 */
	public static interface OnStorageControllerClickedListener {
		
		/**
		 * @param element		the {@link IStorageController} click event
		 */
		public void onStorageControllerClicked(IStorageController element);
	}

	/**
	 * Listener for Medium attachment clicks
	 */
	public static interface OnMediumAttachmentClickedListener {
		
		/**
		 * Handle Medium attachment click event
		 * @param element		the {@link IMediumAttachment} click event
		 */
		public void onMediumAttachmentClicked(IMediumAttachment element);
	}

	private ExpandableListView _listView;
	private ItemAdapter _listAdapter;

	private OnStorageControllerClickedListener _controllerListener;
	private OnMediumAttachmentClickedListener _mediumListener;

	private IMachine _machine;
	private ISystemProperties _systemProperties;
	private Map<IStorageController, List<IMediumAttachment>> _controllers; 

	/**
	 * Load Data
	 */
	private class LoadDataTask extends DialogTask<IMachine, Map<IStorageController, List<IMediumAttachment>>> {

		public LoadDataTask() {
			super(getSherlockActivity(), _machine.getAPI(), R.string.progress_load_storage_controllers);
		}

		@Override
		protected Map<IStorageController, List<IMediumAttachment>> work(IMachine... params) throws Exception {
			_systemProperties = _vmgr.getVBox().getSystemProperties();
			ChipsetType chipset = _machine.getChipsetType();
			for(StorageBus bus : StorageBus.values()) {
				_systemProperties.getMaxInstancesOfStorageBus(chipset, bus);
				_systemProperties.getDeviceTypesForStorageBus(bus);
			}
			Map<IStorageController, List<IMediumAttachment>> controllers = new HashMap<IStorageController, List<IMediumAttachment>>();
			for(IStorageController c : params[0].getStorageControllers()) {
				params[0].clearCacheNamed("getMediumAttachmentsOrController-"+c.getName());
				ArrayList<IMediumAttachment> attachments = params[0].getMediumAttachmentsOfController(c.getName());
				c.getBus(); c.getControllerType();
				for(IMediumAttachment a : attachments) {
					if(a.getMedium()!=null) {
						a.getMedium().clearCache();
						a.getMedium().getName();
						a.getMedium().getBase().getName();
					}
				}
				controllers.put(c, attachments);
			}
			return controllers;
		}

		@Override
		protected void onSuccess(Map<IStorageController, List<IMediumAttachment>> result) {
			super.onSuccess(result);
			_controllers = result;
			_listAdapter = new ItemAdapter(getSherlockActivity(), result);
			_listView.setAdapter(_listAdapter);
			for(int i=0; i<result.keySet().size(); i++)
				_listView.expandGroup(i, true);
		}
	}

	/**
	 * Add Controller
	 */
	private class AddControllerTask extends ActionBarTask<StorageBus, IStorageController> {

		public AddControllerTask() {
			super(getSherlockActivity(), _machine.getAPI());
		}

		@Override
		protected IStorageController work(StorageBus... params) throws Exception {
			IStorageController controller = _machine.addStorageController(params[0].toString(), params[0]);
			controller.getName(); controller.getBus(); controller.getControllerType();
			return controller;
		}

		@Override
		protected void onSuccess(IStorageController result) {
			super.onSuccess(result);
			_controllers.put(result, new ArrayList<IMediumAttachment>());
			_listAdapter.notifyDataSetChanged();
		}
	}

	/**
	 * Delete Controller
	 */
	private class DeleteControllerTask extends ActionBarTask<IStorageController, IStorageController> {

		public DeleteControllerTask() {
			super(getSherlockActivity(), _machine.getAPI());
		}

		@Override
		protected IStorageController work(IStorageController... params) throws Exception {
			_machine.removeStorageController(params[0].getName());
			return params[0];
		}

		@Override
		protected void onSuccess(IStorageController result) {
			super.onSuccess(result);
			_controllers.remove(result);
			_listAdapter.notifyDataSetChanged();
		}
	}

	/**
	 * Detach Medium
	 */
	private class DetachMediumTask extends ActionBarTask<IMediumAttachment, IMediumAttachment> {

		public DetachMediumTask() {
			super(getSherlockActivity(), _machine.getAPI());
		}

		@Override
		protected IMediumAttachment work(IMediumAttachment... params) throws Exception {
			_machine.detachDevice(params[0].getController(), params[0].getPort(), params[0].getDevice());
			return params[0];
		}

		@Override
		protected void onSuccess(IMediumAttachment result) {
			super.onSuccess(result);
			for(IStorageController c : _controllers.keySet())
				if(c.getName().equals(result.getController()))
					_controllers.get(c).remove(result);
			_listAdapter.notifyDataSetChanged();
		}
	}
	
	/**
	 * Move the medium to a different slot within controller.
	 */
	abstract class ListMediumsTask extends ActionBarTask<Void, List<IMedium>> {

		private IStorageController controller;
		private DeviceType deviceType;
		
		public ListMediumsTask(IStorageController controller, DeviceType type) { 
			super(getSherlockActivity(), _machine.getAPI()); 
			this.controller = controller;
			this.deviceType = type;
		}
		
		public abstract List<IMedium> getMediums() throws Exception;

		@Override 
		protected List<IMedium> work(Void...params) throws Exception {
			List<IMedium> mediums = getMediums();
			for(IMedium m : mediums) {
					m.getName();
			}
			return mediums;
		}
		
		@Override
		protected void onSuccess(final List<IMedium> result) {
			super.onSuccess(result);
			final CharSequence []items = new CharSequence[result.size()];
			for(int i=0; i<result.size(); i++)
				items[i] = result.get(i).getName();
			new AlertDialog.Builder(getActivity())
				.setTitle("Select Medium")
				.setItems(items, new DialogInterface.OnClickListener() {
					public void onClick(DialogInterface dialog, int item) {
						new MountTask(controller, deviceType).execute(result.get(item));
					}
				}).show();
		}
	}
	
	/**
	 * List mountable mediums
	 */
	class ListDVDMediumsTask extends ActionBarTask<Void, List<IMedium>> {

		private IStorageController controller;
		
		public ListDVDMediumsTask(IStorageController controller) { 
			super(getSherlockActivity(),_machine.getAPI()); 
			this.controller=controller;
		}

		@Override 
		protected List<IMedium> work(Void...params) throws Exception {
			List<IMedium> mediums = _vmgr.getVBox().getHost().getDVDDrives();
			mediums.addAll( _vmgr.getVBox().getDVDImages() );
			for(IMedium m : mediums) {
				m.getName(); m.getHostDrive();
			}
			return mediums;
		}

		@Override
		protected void onSuccess(final List<IMedium> result) {
			super.onSuccess(result);
			final CharSequence []items = new CharSequence[result.size()+1];
			for(int i=0; i<result.size(); i++) {
				IMedium m = result.get(i);
				items[i] = (m.getHostDrive() ? "Host Drive " : "") + m.getName();
			}
			items[items.length-1] = "No Disc"; 

			new AlertDialog.Builder(getActivity())
			.setTitle("Select Disk")
			.setItems(items, new DialogInterface.OnClickListener() {
				public void onClick(DialogInterface dialog, int item) {
					CharSequence selected = items[item];
					new MountTask(controller, DeviceType.DVD).execute(selected.equals("No Disc") ? null : result.get(item));
				}
			}).show();
		}
	}

	/**
	 * Move the medium to a different slot within controller.
	 */
	class MountTask extends DialogTask<IMedium, IMediumAttachment> {

		private IStorageController controller;
		private DeviceType deviceType;
		
		public MountTask(IStorageController controller, DeviceType type) { 
			super(getSherlockActivity(), _machine.getAPI(), R.string.progress_mounting_medium); 
			this.controller = controller;
			this.deviceType = type;
		}

		@Override 
		protected IMediumAttachment work(IMedium...params) throws Exception {
			IMedium medium = params[0];
			IMediumAttachment attachment = new IMediumAttachment();
			int devicesPerPort = controller.getMaxDevicesPerPortCount();
			for(int i=0; i<controller.getMaxPortCount(); i++) {
				for(int j=0; j<devicesPerPort; j++) {
					boolean isUsed = false;
					for(IMediumAttachment a : _controllers.get(controller)) {
						if(a.getPort()==i && a.getDevice()==j) {
							isUsed=true;
							break;
						}
					}
					if(!isUsed) {
						attachment.setPort(i);
						attachment.setDevice(j);
					}
				}
			}
			Log.d(TAG, "Attaching to slot: " + attachment.getSlot());
			_machine.attachDevice(controller.getName(), attachment.getPort(), attachment.getDevice(), deviceType, medium);
			attachment.setMedium(medium);
			attachment.setType(deviceType);
			attachment.setController(controller.getName());
			return attachment;
		}
		
		@Override
		protected void onSuccess(IMediumAttachment result) {
			super.onSuccess(result);
			Utils.toastShort(getContext(), "Attached medium to slot " + result.getSlot());
			_controllers.get(controller).add(result);
			_listAdapter.notifyDataSetChanged();
		}
	}

	private class ItemAdapter extends BaseExpandableListAdapter {

		private final LayoutInflater _inflater;
		private List<IStorageController> controllers;
		private Map<IStorageController, List<IMediumAttachment>> data;

		public ItemAdapter(Context context, Map<IStorageController, List<IMediumAttachment>> data) {
			_inflater = LayoutInflater.from(context);
			this.data = data;
			update();
		}
		
		private void update() {
			controllers = new ArrayList<IStorageController>(data.keySet().size());
			for(IStorageController controller : data.keySet())
				controllers.add(controller);
		}
		
		@Override
		public void notifyDataSetChanged() {
			update();
			super.notifyDataSetChanged();
			for(int i=0; i<controllers.size(); i++)
				_listView.expandGroup(i, true);
		}

		@Override
		public int getGroupCount() {
			return controllers.size();
		}

		@Override
		public Object getGroup(int groupPosition) {
			return controllers.get(groupPosition);
		}

		@Override
		public long getGroupId(int groupPosition) {
			return groupPosition;
		}

		@Override
		public int getChildrenCount(int groupPosition) {
			return data.get(controllers.get(groupPosition)).size();
		}

		@Override
		public Object getChild(int groupPosition, int childPosition) {
			return data.get(controllers.get(groupPosition)).get(childPosition);
		}

		@Override
		public long getChildId(int groupPosition, int childPosition) {
			return childPosition;
		}

		@Override
		public boolean hasStableIds() {
			return true;
		}

		@Override
		public View getGroupView(int groupPosition, boolean isExpanded, View convertView, ViewGroup parent) {
			final IStorageController controller = (IStorageController)getGroup(groupPosition);
//			if(convertView==null) {
				convertView = _inflater.inflate(R.layout.settings_storage_controller_list_item, parent, false);
				convertView.setTag((TextView)convertView.findViewById(android.R.id.text1));
//			}
			TextView text1 = (TextView)convertView.getTag();
			text1.setText(controller.getName());
			LinearLayout linear = (LinearLayout)convertView;
			for(DeviceType type : _systemProperties.getDeviceTypesForStorageBus(controller.getBus())) {
				ImageButton button = new ImageButton(getActivity());
				button.setFocusable(false);
				if(type.equals(DeviceType.HARD_DISK)) {
					button.setImageResource(VBoxApplication.getInstance().getDrawable(R.drawable.ic_menu_hdd_add));
					button.setOnClickListener(new OnClickListener() {
						@Override
						public void onClick(View v) {
							new ListMediumsTask(controller, DeviceType.HARD_DISK) {
								@Override
								public List<IMedium> getMediums() throws Exception {
									return _vmgr.getVBox().getHardDisks();
								}
							}.execute();
						}
					});
				} else if(type.equals(DeviceType.DVD)) {
					button.setImageResource(VBoxApplication.getInstance().getDrawable(R.drawable.ic_menu_dvd_add));
					button.setOnClickListener(new OnClickListener() {
						@Override
						public void onClick(View v) {
							new ListDVDMediumsTask(controller).execute();
						}
					});
				}
				linear.addView(button, new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT));
			}
			//	        text1.setCompoundDrawablesWithIntrinsicBounds(icon, 0, 0, 0);
			return convertView;
		}

		@Override
		public View getChildView(int groupPosition, int childPosition, boolean isLastChild, View convertView, ViewGroup parent) {
			final IMediumAttachment attachment = (IMediumAttachment)getChild(groupPosition, childPosition);
			if(convertView==null) {
				convertView = _inflater.inflate(R.layout.simple_selectable_list_item, parent, false);
				convertView.setTag((TextView)convertView.findViewById(android.R.id.text1));
			}
			TextView text1 = (TextView)convertView.getTag();
			text1.setText(attachment.getMedium()!=null ? attachment.getMedium().getBase().getName() : "Empty");
			if(attachment.getType().equals(DeviceType.HARD_DISK))
				text1.setCompoundDrawablesWithIntrinsicBounds(VBoxApplication.getInstance().getDrawable(R.drawable.ic_button_hdd), 0, 0, 0);
			if(attachment.getType().equals(DeviceType.DVD))
				text1.setCompoundDrawablesWithIntrinsicBounds(VBoxApplication.getInstance().getDrawable(R.drawable.ic_button_dvd), 0, 0, 0);
			return convertView;
		}

		@Override
		public boolean isChildSelectable(int groupPosition, int childPosition) {
			return true;
		}
	}

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setHasOptionsMenu(true);
		_machine = (IMachine)getArguments().getParcelable(IMachine.BUNDLE);
	}

	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
		View view = inflater.inflate(R.layout.settings_storage_tree, container, false);
		_listView = (ExpandableListView)view.findViewById(R.id.storage_tree);
		_listView.setOnGroupClickListener(new OnGroupClickListener() {
			@Override
			public boolean onGroupClick(ExpandableListView parent, View v, int groupPosition, long id) {
				parent.expandGroup(groupPosition,true);
				parent.setSelectedGroup(groupPosition);
				if(_controllerListener!=null)
					_controllerListener.onStorageControllerClicked((IStorageController)_listAdapter.getGroup(groupPosition));
				return true;
			}
		});
		_listView.setOnChildClickListener(new OnChildClickListener() {
			@Override
			public boolean onChildClick(ExpandableListView parent, View v, int groupPosition, int childPosition, long id) {
				parent.setSelectedChild(groupPosition, childPosition, true);
				if(_mediumListener!=null)
					_mediumListener.onMediumAttachmentClicked((IMediumAttachment)_listAdapter.getChild(groupPosition, childPosition));
				return true;
			}
		});
		registerForContextMenu(_listView);
		return view;
	}

	@Override
	public void onActivityCreated(Bundle savedInstanceState) {
		super.onActivityCreated(savedInstanceState);
		if(getParentFragment() instanceof OnStorageControllerClickedListener)
			_controllerListener = (OnStorageControllerClickedListener)getParentFragment();
		if(getParentFragment() instanceof OnMediumAttachmentClickedListener)
			_mediumListener = (OnMediumAttachmentClickedListener)getParentFragment();
	}

	@Override
	public void onStart() {
		super.onStart();
		if(_controllers==null)
			new LoadDataTask().execute(_machine);
		else {
			_listAdapter = new ItemAdapter(getSherlockActivity(), _controllers);
			_listView.setAdapter(_listAdapter);
			for(int i=0; i<_controllers.keySet().size(); i++)
				_listView.expandGroup(i, true);
		}
	}

	@Override
	public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
		inflater.inflate(R.menu.storage_actions, menu);
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		switch(item.getItemId()) {
			case R.id.menu_add_controller:
				List<CharSequence> itemList = new ArrayList<CharSequence>(5);
				ChipsetType chipset = _machine.getChipsetType();
				for(StorageBus bus : Utils.removeNull(StorageBus.values())) {
					if(getNumStorageControllersOfType(bus)<_systemProperties.getMaxInstancesOfStorageBus(chipset, bus))
						itemList.add(bus.toString());
				}
				final CharSequence[] items = itemList.toArray(new CharSequence[itemList.size()]);
				new AlertDialog.Builder(getActivity())
					.setTitle("Controller Type")
					.setItems(items, new DialogInterface.OnClickListener() {
						public void onClick(DialogInterface dialog, int item) {
							final StorageBus bus = StorageBus.fromValue(items[item].toString());
							new AddControllerTask().execute(bus);
							Utils.toastLong(getActivity(), bus.toString());
						}
					}).show();
				return true;
			case R.id.menu_refresh:
				new LoadDataTask().execute(_machine);
				return true;
		}
		return false;
	}
	
	private int getNumStorageControllersOfType(StorageBus bus) {
		int numControllers = 0;
		for(IStorageController controller : _controllers.keySet()) {
			if(controller.getBus().equals(bus))
				numControllers++;
		}
		return numControllers;
	}
	
	@Override
	public void onCreateContextMenu(ContextMenu menu, View v, ContextMenuInfo menuInfo) {
		menu.add(Menu.NONE, R.id.menu_delete, Menu.NONE, R.string.delete);
	}
	
	@Override
	public boolean onContextItemSelected(android.view.MenuItem item) {
		ExpandableListContextMenuInfo info = (ExpandableListContextMenuInfo) item.getMenuInfo();
		int groupNum = ExpandableListView.getPackedPositionGroup(info.packedPosition);
        switch(ExpandableListView.getPackedPositionType(info.packedPosition)) {
        	case ExpandableListView.PACKED_POSITION_TYPE_GROUP:
        		IStorageController controller = (IStorageController)_listAdapter.getGroup(groupNum);
        		new DeleteControllerTask().execute(controller);
        		break;
        	case ExpandableListView.PACKED_POSITION_TYPE_CHILD:
        		int childNum = ExpandableListView.getPackedPositionChild(info.packedPosition);
        		IMediumAttachment attachment = (IMediumAttachment)_listAdapter.getChild(groupNum, childNum);
        		new DetachMediumTask().execute(attachment);
        		break;
        }
		return false;
	}
}