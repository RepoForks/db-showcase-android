package cz.koto.misak.dbshowcase.android.mobile.db.realm;

import android.os.Parcel;

import org.parceler.Parcels;

/**
 * https://gist.github.com/cmelchior/72c35fcb55cec33a71e1
 */
// Specific class for a RealmList<Bar> field
public class RealmStringListParcelConverter extends RealmListParcelConverter<RealmString> {

    @Override
    public void itemToParcel(RealmString input, Parcel parcel) {
        parcel.writeParcelable(Parcels.wrap(input), 0);
    }

    @Override
    public RealmString itemFromParcel(Parcel parcel) {
        return Parcels.unwrap(parcel.readParcelable(RealmString.class.getClassLoader()));
    }
}