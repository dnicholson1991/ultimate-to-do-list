# **Ultimate To-Do List for Android**

Ultimate To-Do List is a to-do list manager for Android phones and tablets and Wear OS watches that has an extensive set of features. This app was previously available only on Google Play and was converted to open source in December 2024.

## **Features Offered**

This app has an extensive feature set, which includes:

- Synchronization of tasks with Google Tasks and Toodledo.com.
- Flexible reminders, including an option to nag you until the task is done.
- Advanced task repeating patterns.
- Organize tasks by folder, contaxt, goal, and location.
- Prioritize tasks with 5 priority levels and a status field.
- Support for subtasks.
- Flexible options to filter, sort, and display your tasks.
- Time tracking, including both the estimated and actual time for a task.
- Support for Toodledo's sharing and collaboration features.
- A Wear OS app that allows you to browse and check-off tasks and snooze reminders.
- Customizable split-screen views for larger devices.
- A notes area for inofrmation not linked to a to-do item.
- A voice mode for task entry.

## **Before You Build**

Before the app can sync with Toodledo, you will need to obtain credentials for their API. You can do that on [Toodledo's developer website](https://api.toodledo.com/3/account/doc_register.php). Once you have a client ID and client secret, enter those into the file ToodledoInterface.java.

If you want to support sync with Google Tasks on devies without Google Play Services, such as Amazon's Kindle Fire, you will need to obtain API credentials from [Google Cloud](https://cloud.google.com). You will need a client ID, client secret, and API key, which you can enter into the file GTasksInterface.java. You will also need to enter the API key into the file utl/google-services.json, within the "api_key" section.

## **Important Considerations**

Because this app used to be on Google Play and previously meant to be a for-profit project, there is significant code and UI elements in place that are no longer applicable. These elements were not deleted in order to make it easier to use this app as the basis for a for-profit project of your own. For Kindle Fire devices, the source includes an "Amazon" build variant which works with the purchasing system on the Amazon app store.

While it was on Google Play, the app offered paid add-ons, which are now free. In the file Util.java, there is a flag called IS_FREE which determines whether or not the paid add-ons are free.

The app also included advertising while it was on Google Play. The IS_FREE flag in Util.java also determines whether ads will show. To restore ads in the app, it will be necessary to update the AndroidManifest.xml file with the app ID from Admob. It will also be necessary to update the ad IDs in the file BannerAd.java.

The app used to communicate with a backend server to report statistics and log errors. This capability has been disabled by setting the DISABLE_BACKEND flag to true in Api.java. If you want to set up your own backend and have the app report stats and errors, then set this flag to false and enter your server URL and access credentials into Api.java.

## Licensing

The source is distributed under the MIT license. Feel free to copy the code and use it in your own projects, including for-profit and closed source.