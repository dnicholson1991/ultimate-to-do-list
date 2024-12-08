package com.customsolutions.android.utl;

// This class holds information about a specific collaborator:

public class UTLCollaborator
{
	public long _id;
	public long account_id;
	public String remote_id;
	public String name;
	public boolean reassignable;
	public boolean sharable;
	
	public UTLCollaborator()
	{
		_id = 0;
		account_id = 0;
		remote_id = "";
		name = "";
		reassignable = false;
		sharable = false;
	}
}
