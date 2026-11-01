import unittest
import numpy as np
import pandas as pd
from study import signals,outcome

def frame(n=300):
    ix=pd.bdate_range('2020-01-01',periods=n)
    f=pd.DataFrame({'open':10.,'high':10.2,'low':9.8,'close':10.,'volume':100000.,'split':1.,'dividend':0.,'factor':1.,'atr':.4},index=ix)
    for k in ['open','high','low','close']:f['nominal_'+k]=f[k]
    return f

class StudyTests(unittest.TestCase):
    def test_past_only_signals(self):
        f=frame(); before=signals(f.iloc[:270]);f.loc[f.index[270]:,'close']=100
        pd.testing.assert_frame_equal(before,signals(f).iloc[:270])
    def test_no_future_entry_filter(self):
        f=frame(); d=f.index[260];entry=f.index[261]
        f.loc[entry,'open']=np.nan
        self.assertEqual(outcome(f,f.index,d,'momentum_burst')['status'],'missing_entry')
    def test_stop_first_and_fees(self):
        f=frame();d=f.index[260];e=f.index[261]
        f.loc[e,['nominal_low','nominal_high']]=[9.,12.]
        r=outcome(f,f.index,d,'momentum_burst',fee=2.95,bps=0)
        self.assertEqual(r['reason'],'stop');self.assertAlmostEqual(r['pnl_eur'],99*(9.76-10)-5.9)
    def test_gap_stop(self):
        f=frame();d=f.index[260]
        f.loc[f.index[261],'nominal_low']=10
        f.loc[f.index[262],['nominal_open','nominal_low']]=[9.,8.9]
        r=outcome(f,f.index,d,'momentum_burst',fee=0,bps=0)
        self.assertEqual(r['reason'],'gap_stop');self.assertAlmostEqual(r['pnl_eur'],-100)
    def test_end_not_closed_at_invented_price(self):
        f=frame();f['nominal_low']=10;f['nominal_high']=10
        self.assertEqual(outcome(f,f.index,f.index[-2],'momentum_burst')['status'],'open_at_cutoff')
    def test_split_keeps_value(self):
        f=frame();d=f.index[260];f['nominal_low']=10;f['nominal_high']=10
        f.loc[f.index[262],'split']=2
        for k in ['open','high','low','close']:f.loc[f.index[262]:,'nominal_'+k]/=2
        r=outcome(f,f.index,d,'momentum_burst',fee=0,bps=0)
        self.assertAlmostEqual(r['pnl_eur'],0)
    def test_dividend_not_on_entry(self):
        f=frame();d=f.index[260];f['nominal_low']=10;f['nominal_high']=10
        f.loc[f.index[261],'dividend']=1
        f.loc[f.index[262],'dividend']=.1
        r=outcome(f,f.index,d,'momentum_burst',fee=0,bps=0)
        self.assertAlmostEqual(r['pnl_eur'],10)
    def test_classic_named_proxy(self):
        self.assertIn('ep_classic_price_proxy',signals(frame()).columns)
        self.assertNotIn('ep_classic_confirmed',signals(frame()).columns)

if __name__=='__main__':unittest.main()
