import unittest
import numpy as np
import pandas as pd
from italy_ml import FEATURES, COST, features, training_rows, samples, summary

class ResearchTests(unittest.TestCase):
    def frame(self):
        days=pd.bdate_range('2016-01-01',periods=1200)
        c=np.arange(1200)*.01+20
        return pd.DataFrame({'open':c,'high':c+1,'low':c-1,'close':c,'adjclose':c,'volume':1000000},index=days)

    def test_future_prices_cannot_change_past_features(self):
        a=self.frame(); b=a.copy(); b.iloc[700:,:]*=3
        pd.testing.assert_frame_equal(features(a).iloc[:700],features(b).iloc[:700])

    def test_training_purges_cross_boundary_outcomes(self):
        df=pd.DataFrame({'signal':pd.to_datetime(['2020-11-01','2020-12-20','2021-01-02']), 'exit':pd.to_datetime(['2020-12-01','2021-01-10','2021-02-01'])})
        self.assertEqual(training_rows(df,pd.Timestamp('2021-01-01')).index.tolist(),[0])

    def test_entry_is_next_session_and_periods_do_not_overlap(self):
        f=self.frame(); df=samples({'ENI':f})
        self.assertGreater(len(df),0)
        self.assertTrue((df.entry>df.signal).all())
        self.assertTrue((df.exit>df.entry).all())
        self.assertEqual(df.exit.iloc[0],df.entry.iloc[1])
        first=df.iloc[0]
        self.assertAlmostEqual(first.gross,f.loc[first.exit,'open']/f.loc[first.entry,'open']-1)
        self.assertAlmostEqual(first.net,first.gross-COST)

    def test_missing_exit_not_filled(self):
        f=self.frame(); a=samples({'ENI':f,'SOL':f.copy()})
        missing=a[a.ticker=='SOL'].exit.iloc[0]
        b=samples({'ENI':f,'SOL':f.drop(missing)})
        self.assertFalse(((b.ticker=='SOL')&(b.exit==missing)).any())

    def test_loss_summary(self):
        df=pd.DataFrame({'net':[-.02,-.01],'gross':[-.01,0],'signal':pd.to_datetime(['2021-01-01','2021-02-01'])})
        s=summary(df); self.assertEqual(s['profit_factor'],0); self.assertEqual(s['win_rate_pct'],0)

if __name__=='__main__': unittest.main()
