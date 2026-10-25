package com.rasha.omni;

final class PreviewPolicyTest {
    static void run() {
        PreviewPolicy p = new PreviewPolicy(false, false);
        T.ok(p.allowed(), "previews are allowed by default");
        T.ok(!p.record(true, 1200) && p.allowed(), "a quick successful preview changes nothing");
        T.ok(!p.record(false, 0) && !p.record(false, 0), "two failures in a row are tolerated");
        T.ok(p.allowed(), "still allowed after two failures");
        T.ok(p.record(false, 0) && !p.allowed() && p.autoOff(), "the third failure switches to artwork-only mode");
        T.ok(!p.record(false, 0), "the switch is reported once");
        p.setUserOff(false);
        T.ok(p.allowed() && !p.autoOff(), "turning previews on again resets the automatic switch");
        PreviewPolicy q = new PreviewPolicy(false, false);
        q.record(false, 0); q.record(false, 0); q.record(true, 900);
        q.record(false, 0); q.record(false, 0);
        T.ok(q.allowed(), "a success resets the count of bad previews");
        PreviewPolicy s = new PreviewPolicy(false, false);
        s.record(true, 9000); s.record(true, 9000);
        T.ok(s.record(true, 9000) && !s.allowed(), "slow previews count as bad");
        PreviewPolicy w = new PreviewPolicy(false, false);
        T.ok(w.markUnsupported() && !w.allowed() && !w.markUnsupported(), "a detected hardware limit switches previews off at once, reported once");
        w.setUserOff(false);
        T.ok(w.allowed(), "the user can turn previews back on to try again");
        T.ok(!new PreviewPolicy(true, false).allowed(), "user setting off wins");
        PreviewPolicy u = new PreviewPolicy(true, false);
        u.setUserOff(true);
        T.ok(!u.allowed() && u.userOff(), "user off stays off");
    }
}
