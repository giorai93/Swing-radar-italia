import copy, unittest
import numpy as np
import pandas as pd
from portfolio_validation import buy_quantity, candidates, prepare_result, simulate

class PortfolioTests(unittest.TestCase):
    def pack(self,split=False,dividend=0):
        names=[f'T{i}' for i in range(10)]
        bars={t:[['2021-01-04',10,10,1,0],['2021-01-05',5 if split else 10,5 if split else 10,2 if split else 1,dividend]] for t in names}
        return {'cutoff_exclusive':'2026-01-01','calendar':['2021-01-04','2021-01-05'],'entries':{'2021-01-04':{'signal':'2021-01-01','candidates':[{'ticker':t,'r5':-.1,'r60':.1} for t in names]}},'bars':bars}

    def test_cash_fees_and_integer_orders(self):
        r,c,tr,o=simulate(self.pack(),'reversal5',1000,bps=0)
        self.assertAlmostEqual(r['final_equity_eur'],941)
        self.assertAlmostEqual(r['fees_eur'],59)
        self.assertTrue(all(x['qty']==9 for x in o))
        self.assertTrue(all(x['cash']>=0 for x in c))

    def test_flat_price_no_cost_no_profit(self):
        r,*_=simulate(self.pack(),'reversal5',1000,fee=0,bps=0)
        self.assertAlmostEqual(r['return_pct'],0)

    def test_split_does_not_create_profit_or_loss(self):
        r,*_=simulate(self.pack(split=True),'reversal5',1000,fee=0,bps=0)
        self.assertAlmostEqual(r['final_equity_eur'],1000)

    def test_dividend_reserved_counted_once(self):
        r,c,tr,o=simulate(self.pack(dividend=.5),'reversal5',1000,bps=0)
        self.assertAlmostEqual(r['dividends_reserved_eur'],45)
        self.assertAlmostEqual(r['final_equity_eur'],986)
        self.assertAlmostEqual(sum(t['pnl'] for t in tr),-14)
        self.assertAlmostEqual(c[-1]['cash'],941)

    def test_missing_future_exit_never_removes_entry(self):
        p=self.pack();p['bars']['T0'].pop()
        r,c,tr,o=simulate(p,'reversal5',1000,bps=0)
        self.assertEqual(len(o),10)
        self.assertIn('T0',r['unresolved_positions'])
        self.assertEqual(r['stale_position_days'],1)
        self.assertAlmostEqual(r['final_equity_eur']-r['hypothetical_zero_unresolved_equity_eur'],90)

    def test_missing_open_not_replaced(self):
        p=self.pack();p['bars']['T0'].pop(0)
        r,c,tr,o=simulate(p,'reversal5',1000,bps=0)
        self.assertEqual(r['missing_buy_opens'],1)
        self.assertEqual(r['trades'],9)

    def test_unaffordable_order_is_skipped(self):
        self.assertEqual(buy_quantity(100,200,2.95,.0005),0)

    def test_reserved_period_rejected(self):
        p=self.pack();p['calendar'].append('2026-01-02')
        with self.assertRaises(ValueError):simulate(p,'reversal5',1000)

    def test_nominal_price_reconstruction(self):
        ts=[int(pd.Timestamp(d,tz='UTC').timestamp()) for d in ['2024-01-02','2024-01-03']]
        r={'timestamp':ts,'indicators':{'quote':[{'open':[5,5],'close':[5,5],'high':[5,5],'low':[5,5],'volume':[100,100]}],'adjclose':[{'adjclose':[5,5]}]},'events':{'splits':{'x':{'date':ts[1],'numerator':2,'denominator':1}}}}
        f=prepare_result(r)
        self.assertEqual(f.nominal_close.tolist(),[10,5])

    def test_selection_not_dependent_on_future_bars(self):
        dates=pd.bdate_range('2019-01-01',periods=400);c=np.linspace(20,30,400)
        f=pd.DataFrame({'open':c,'high':c+1,'low':c-1,'close':c,'adjclose':c,'volume':1000000,'nominal_close':c},index=dates)
        past=candidates({'A':f},dates[300])
        g=f.iloc[:301].copy()
        self.assertEqual(past,candidates({'A':g},dates[300]))

if __name__=='__main__':unittest.main()
