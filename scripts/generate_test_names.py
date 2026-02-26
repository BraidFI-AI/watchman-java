#!/usr/bin/env python3
"""
Generate static test dataset for load testing.
Creates 9,000 realistic names by combining common first and last names.
"""

import json
from pathlib import Path
import random

def generate_clean_names(count: int = 9000, seed: int = 42) -> list:
    """Generate realistic names by combining common names"""
    random.seed(seed)  # Fixed seed for reproducible dataset
    
    # Common first names (US Census data)
    first_names = [
        "James", "Mary", "Robert", "Patricia", "John", "Jennifer", "Michael", "Linda",
        "David", "Barbara", "William", "Elizabeth", "Richard", "Susan", "Joseph", "Jessica",
        "Thomas", "Sarah", "Christopher", "Karen", "Charles", "Lisa", "Daniel", "Nancy",
        "Matthew", "Betty", "Anthony", "Margaret", "Mark", "Sandra", "Donald", "Ashley",
        "Steven", "Emily", "Andrew", "Kimberly", "Paul", "Donna", "Joshua", "Michelle",
        "Kenneth", "Carol", "Kevin", "Amanda", "Brian", "Melissa", "George", "Deborah",
        "Timothy", "Stephanie", "Ronald", "Dorothy", "Edward", "Rebecca", "Jason", "Sharon",
        "Jeffrey", "Laura", "Ryan", "Cynthia", "Jacob", "Amy", "Gary", "Kathleen",
        "Nicholas", "Angela", "Eric", "Shirley", "Jonathan", "Brenda", "Stephen", "Emma",
        "Larry", "Anna", "Justin", "Pamela", "Scott", "Nicole", "Brandon", "Helen",
        "Benjamin", "Samantha", "Samuel", "Katherine", "Raymond", "Christine", "Gregory", "Debra",
        "Frank", "Rachel", "Alexander", "Carolyn", "Patrick", "Janet", "Jack", "Maria",
        "Dennis", "Catherine", "Jerry", "Heather", "Tyler", "Diane", "Aaron", "Olivia",
        "Jose", "Julie", "Adam", "Joyce", "Nathan", "Victoria", "Henry", "Ruth",
        "Zachary", "Virginia", "Douglas", "Lauren", "Peter", "Kelly", "Kyle", "Christina",
        "Noah", "Joan", "Ethan", "Evelyn", "Jeremy", "Judith", "Christian", "Andrea",
        "Walter", "Hannah", "Keith", "Megan", "Austin", "Cheryl", "Roger", "Jacqueline",
        "Terry", "Martha", "Sean", "Madison", "Gerald", "Teresa", "Carl", "Gloria",
        "Dylan", "Sara", "Harold", "Janice", "Jordan", "Kathryn", "Jesse", "Ann"
    ]
    
    # Common last names (US Census data)  
    last_names = [
        "Smith", "Johnson", "Williams", "Brown", "Jones", "Garcia", "Miller", "Davis",
        "Rodriguez", "Martinez", "Hernandez", "Lopez", "Gonzalez", "Wilson", "Anderson", "Thomas",
        "Taylor", "Moore", "Jackson", "Martin", "Lee", "Perez", "Thompson", "White",
        "Harris", "Sanchez", "Clark", "Ramirez", "Lewis", "Robinson", "Walker", "Young",
        "Allen", "King", "Wright", "Scott", "Torres", "Nguyen", "Hill", "Flores",
        "Green", "Adams", "Nelson", "Baker", "Hall", "Rivera", "Campbell", "Mitchell",
        "Carter", "Roberts", "Gomez", "Phillips", "Evans", "Turner", "Diaz", "Parker",
        "Cruz", "Edwards", "Collins", "Reyes", "Stewart", "Morris", "Morales", "Murphy",
        "Cook", "Rogers", "Gutierrez", "Ortiz", "Morgan", "Cooper", "Peterson", "Bailey",
        "Reed", "Kelly", "Howard", "Ramos", "Kim", "Cox", "Ward", "Richardson",
        "Watson", "Brooks", "Chavez", "Wood", "James", "Bennett", "Gray", "Mendoza",
        "Ruiz", "Hughes", "Price", "Alvarez", "Castillo", "Sanders", "Patel", "Myers",
        "Long", "Ross", "Foster", "Jimenez", "Powell", "Jenkins", "Perry", "Russell",
        "Sullivan", "Bell", "Coleman", "Butler", "Henderson", "Barnes", "Gonzales", "Fisher",
        "Vasquez", "Simmons", "Romero", "Jordan", "Patterson", "Alexander", "Hamilton", "Graham",
        "Reynolds", "Griffin", "Wallace", "Moreno", "West", "Cole", "Hayes", "Bryant",
        "Herrera", "Gibson", "Ellis", "Tran", "Medina", "Aguilar", "Stevens", "Murray",
        "Ford", "Castro", "Marshall", "Owens", "Harrison", "Fernandez", "McDonald", "Woods"
    ]
    
    names = []
    for i in range(count):
        first = random.choice(first_names)
        last = random.choice(last_names)
        # Add middle initial occasionally for variety
        if i % 3 == 0:
            middle = random.choice("ABCDEFGHIJKLMNOPQRSTUVWXYZ")
            name = f"{first} {middle}. {last}"
        else:
            name = f"{first} {last}"
        names.append(name)
    
    return names

def main():
    print("Generating 9,000 clean names for load testing...")
    
    # Generate names
    clean_names = generate_clean_names(9000)
    
    # Create test-data directory if it doesn't exist
    data_dir = Path(__file__).parent.parent / "test-data"
    data_dir.mkdir(exist_ok=True)
    
    # Save to JSON file
    output_file = data_dir / "clean_names_9000.json"
    with open(output_file, 'w') as f:
        json.dump(clean_names, f, indent=2)
    
    print(f"✓ Generated {len(clean_names)} names")
    print(f"✓ Saved to: {output_file}")
    print(f"\nSample names:")
    for name in clean_names[:10]:
        print(f"  - {name}")

if __name__ == "__main__":
    main()
