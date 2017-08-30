# PeekAndPop

PeekAndPop is an open source Android library, inspired by Instagram's latest 3D touch feature.
As 3D touch is not available for android, this library uses long hold.

Peek and pop allows you to long click on a view to "peek" and see a larger view in the centre of the screen.
The pop feature can be imitated using fling to action gestures or from a hold and release event.

### Features:

- Create a basic peek and pop that shows on long click and is dismissed on touch up.
- Fling to action, where flinging the view upwards or downwards (sidewards in landscape) triggers an event.
- Specify views within the peek view layout that can listen to long hold events (if the user holds the view for a 
certain amount of time).
- Specify views within the peek view layout that can listen to hold and release events (where the user holds the view
 and then releases).

## Demo app
<a href="https://github.com/shalskar/PeekAndPopDemo"><img src="http://i.giphy.com/14c7kKoK7Pizlu.gif" width="220" ></a>
#### https://github.com/shalskar/PeekAndPopDemo
<br>

### Getting started:

This library is hosted on Jitpack.io, which means to use it you will have to add the following to your root `build.gradle` file.

```gradle
allprojects {
	repositories {
		...
		maven { url "https://jitpack.io" }
	}
}
```

And then you will need to add the following dependency to your applications `build.gradle` file.

```gradle
dependencies {
	compile 'com.github.shalskar:PeekAndPop:1.1.0'
}
```

### Usage:

Basic usage is easy, simply provide an activity instance, a layout resource for the peek and pop and 1 or more views that will show the peek and pop when long clicked.


```java
PeekAndPop peekAndPop = new PeekAndPop.Builder(this)
                .peekLayout(R.layout.peek_view)
                .longClickViews(view)
                .build();
```

You can get the peek view by calling `getPeekView()` on the `PeekAndPop` object, and use `findViewById()` to get access any views in the peek layout.

```java
View peekView = peekAndPop.getPeekView();
ImageView imageView = peekView.findViewById(R.id.image_view);
TextView textView = peekView.findViewById(R.id.text_view);
```

Often you will want to have the peek and pop shown when an item in a list (or other scrollable view) is clicked on, to ensure the peek and pop works correctly, you will have to add this line of code:


```java
                .parentViewGroupToDisallowTouchEvents(viewGroup)
```

#### More options:

##### Listening for peek or pop events

<a href="url"><img src="http://i.giphy.com/XZJgp2gRSmg6Y.gif" width="220" ></a>
<br>


You can set an `OnGeneralActionListener` to receive `onPeek()` and `onPop()` events:

```java           
.onGeneralActionListener(new PeekAndPop.OnGeneralActionListener() {
        @Override
        public void onPeek(View longClickView, int position) {
                        
        }

        @Override
        public void onPop(View longClickView, int position) {

        }
})
```

The parameters being the view that was long clicked and the position of that view.

##### Listening for fling to action events

<a href="url"><img src="http://i.giphy.com/p0QnMHNJXU1wI.gif" width="220" ></a>
<br>


If you provide an `OnFlingToActionListener` then you will automatically be able to fling the view upwards or downwards and listen for those events:

```java
.onFlingToActionListener(new PeekAndPop.OnFlingToActionListener() {
        @Override
        public void onFlingToAction(View longClickView, int position, int direction) {
                        
        }
})
```

The parameters being the same as above and the direction in which the view was flung. (`FLUNG_UPWARDS` or `FLUNG_DOWNWARDS`.)

If you only want to listen for one direction you can set that like so:

```java           
.flingTypes(true, false)
```

And if you don't want to animate the view when flinging:

```java
.animateFling(false)
```

##### Listening for hold, leave and release events

<a href="url"><img src="http://i.giphy.com/PSwj0k9tNj9bq.gif" width="220" ></a>
<br>


Similar to Instagram, you can specify views inside the peek layout that will trigger events if the user holds, leaves or releases on them:

```java
.onHoldAndReleaseListener(new PeekAndPop.OnHoldAndReleaseListener() {
        @Override
        public void onHold(View view, int position) {
                        
        }
        @Override
        public void onLeave(View view, int position) {
                        
        }
        
        @Override
        public void onRelease(View view, int position) {
                        
        }
})
```

And then you must specify the id's of the views you would like to receive events from after you have built the PeekAndPop object:

```java
peekAndPop.addHoldAndReleaseView(R.id.view);
peekAndPop.addHoldAndReleaseView(R.id.view2);
```

##### Listening for long hold events

<a href="url"><img src="http://i.giphy.com/VxGb7FljuJHuU.gif" width="220" ></a>
<br>

You can specify views inside the peek layout that will trigger an event if the user holds their finger/thumb over the view for a certain duration (default is 850ms). If `receiveMultipleEvents` is true than events will be contiuously triggered until the user moves their finger/thumb off the view:

onEnter events are also fired when the user's touch first enters the view's bounds.

```java
.onLongHoldListener(new PeekAndPop.OnLongHoldListener() {
        @Override
        public void onEnter(View view, int position) {
                        
        }
        
        @Override
        public void onLongHold(View view, int position) {
                        
        }
})
```

And then you must specify the id's of the views you would like to receive events from after you have built the PeekAndPop object, aswell as a boolean argument whether or not you want to receive multiple events:

```java
peekAndPop.addLongHoldView(R.id.view, true);
peekAndPop.addLongHoldView(R.id.view, false);
```


The second argument determines whether the view will send multiple events or not. If false, then the user must move their finger/thumb off the view then back on to trigger another event.


### License

```
Copyright 2016 Vincent Te Tau

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
```
