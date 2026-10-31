import sys, unittest
from pathlib import Path
from lxml import etree as E
sys.path.insert(0,str(Path(__file__).resolve().parents[1]/'tools'))
from esef import numeric
class Numbers(unittest.TestCase):
    def fact(self,text,**attrs):
        e=E.Element('fact',**attrs);e.text=text;return e
    def test_comma_scale_sign(self):
        self.assertEqual(numeric(self.fact('1.234,5',format='ixt:num-comma-decimal',scale='3',sign='-')),-1234500)
    def test_dot(self):
        self.assertEqual(numeric(self.fact('1,234.50',format='ixt:num-dot-decimal')),1234.5)
    def test_zero(self):
        self.assertEqual(numeric(self.fact('—',format='ixt:fixed-zero')),0)
    def test_unsupported(self):
        with self.assertRaises(ValueError):numeric(self.fact('12',format='ixt:unknown'))
    def test_contradictory_zero(self):
        with self.assertRaises(ValueError):numeric(self.fact('0.10',format='ixt:fixed-zero'))
    def test_nil(self):
        e=self.fact('');e.set('{http://www.w3.org/2001/XMLSchema-instance}nil','true');self.assertIsNone(numeric(e))
if __name__=='__main__':unittest.main()
