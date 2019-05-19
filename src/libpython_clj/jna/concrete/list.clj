(ns libpython-clj.jna.concrete.list
  (:require [libpython-clj.jna.base
             :refer [def-pylib-fn
                     ensure-pyobj
                     ensure-pytuple
                     ensure-pydict
                     size-t-type
                     *python-library*]
             :as libpy-base]
            [tech.jna :as jna])
  (:import [com.sun.jna Pointer]))

(def-pylib-fn PyList_Check
  "Return true if p is a list object or an instance of a subtype of the list type."
  Integer
  [p ensure-pyobj])


(def-pylib-fn PyList_New
  "Return value: New reference.

   Return a new list of length len on success, or NULL on failure.

   Note

   If len is greater than zero, the returned list object’s items are set to NULL. Thus
   you cannot use abstract API functions such as PySequence_SetItem() or expose the
   object to Python code before setting all items to a real object with
   PyList_SetItem()."
  Pointer
  [len jna/size-t])


(def-pylib-fn PyList_Size
  "Return the length of the list object in list; this is equivalent to len(list) on a
  list object."
  size-t-type
  [list ensure-pyobj])


(def-pylib-fn PyList_GetItem
  "Return value: Borrowed reference.

   Return the object at position index in the list pointed to by list. The position must
   be positive, indexing from the end of the list is not supported. If index is out of
   bounds, return NULL and set an IndexError exception."
  Pointer
  [list ensure-pyobj]
  [index jna/size-t])


(def-pylib-fn PyList_SetItem
  "Set the item at index index in list to item. Return 0 on success or -1 on failure.

   Note

   This function “steals” a reference to item and discards a reference to an item
   already in the list at the affected position."
  Integer
  [list ensure-pyobj]
  [index jna/size-t]
  [item ensure-pyobj])


(def-pylib-fn PyList_Insert
  "Insert the item item into list list in front of index index. Return 0 if successful;
  return -1 and set an exception if unsuccessful. Analogous to list.insert(index,
  item)."
  Integer
  [list ensure-pyobj]
  [index jna/size-t]
  [item ensure-pyobj])


(def-pylib-fn PyList_Append
  "Append the object item at the end of list list. Return 0 if successful; return -1 and
  set an exception if unsuccessful. Analogous to list.append(item)."
  Integer
  [list ensure-pyobj]
  [item ensure-pyobj])


(def-pylib-fn PyList_GetSlice
  "Return value: New reference.

   Return a list of the objects in list containing the objects between low and
   high. Return NULL and set an exception if unsuccessful. Analogous to
   list[low:high]. Negative indices, as when slicing from Python, are not supported."
  Pointer
  [list ensure-pyobj]
  [low jna/size-t]
  [high jna/size-t])


(def-pylib-fn PyList_SetSlice
  "Set the slice of list between low and high to the contents of itemlist. Analogous to
  list[low:high] = itemlist. The itemlist may be NULL, indicating the assignment of an
  empty list (slice deletion). Return 0 on success, -1 on failure. Negative indices, as
  when slicing from Python, are not supported."
  Integer
  [list ensure-pyobj]
  [low jna/size-t]
  [high jna/size-t]
  [itemlist ensure-pyobj])


(def-pylib-fn PyList_Sort
  "Sort the items of list in place. Return 0 on success, -1 on failure. This is
  equivalent to list.sort()."
  Integer
  [list ensure-pyobj])


(def-pylib-fn PyList_Reverse
  "Reverse the items of list in place. Return 0 on success, -1 on failure. This is the
  equivalent of list.reverse()."
  Integer
  [list ensure-pyobj])


(def-pylib-fn PyList_AsTuple
  "Return value: New reference.

   Return a new tuple object containing the contents of list; equivalent to
   tuple(list)."
  Pointer
  [list ensure-pyobj])
