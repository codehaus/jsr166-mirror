#include <iostream>
#include <map>

using namespace std;


int main (int argc, const char* const* argv) {
    map<string, int> m;
    for (int i = 1; i < argc; ++i) {
        string word(argv[i]);
        ++m[word];
    }
    // map is really a sorted map, so this prints words in
    // lexicographic order.
    for (map<string, int>::const_iterator it = m.begin(); it != m.end(); ++it) {
        cout << it->second << " " << it->first << "\n";
    }
}
